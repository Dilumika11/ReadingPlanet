package com.epms.service;

import com.epms.dto.request.QualityCheckRequest;
import com.epms.dto.request.ReadyForPrintingRequest;
import com.epms.dto.response.ManuscriptSummary;
import com.epms.entity.Author;
import com.epms.entity.AuthorApproval;
import com.epms.entity.BookDesign;
import com.epms.entity.Manuscript;
import com.epms.entity.PublishedBook;
import com.epms.entity.QualityCheck;
import com.epms.enums.ManuscriptStatus;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AuthorApprovalRepository;
import com.epms.repository.BookDesignRepository;
import com.epms.repository.CategoryRepository;
import com.epms.repository.ManuscriptRepository;
import com.epms.repository.PublishedBookRepository;
import com.epms.repository.QualityCheckRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Epic 2 (US16 - US20): design versions, the author's design approval,
 * the production quality check and handing the book over for printing.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DesignProductionService {

    public static final long MAX_COVER_BYTES = 5L * 1024 * 1024;
    public static final long MAX_PDF_BYTES = 25L * 1024 * 1024;

    /** Production quality checklist (US19); every item must pass. */
    public static final Map<String, String> CHECKLIST = new LinkedHashMap<>();

    static {
        CHECKLIST.put("coverResolution", "Cover artwork is print resolution and matches the trim size");
        CHECKLIST.put("layoutMatchesManuscript", "Interior layout matches the accepted manuscript");
        CHECKLIST.put("printFileValid", "Print-ready PDF has bleed, crop marks and embedded fonts");
        CHECKLIST.put("metadataCorrect", "Title, author name and spine text are correct");
        CHECKLIST.put("proofread", "Final proofread completed");
    }

    private final ManuscriptRepository manuscriptRepository;
    private final BookDesignRepository designRepository;
    private final AuthorApprovalRepository approvalRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final PublishedBookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final ManuscriptWorkflow workflow;
    private final ManuscriptQueryService queries;
    private final DocumentStorageService storage;
    private final AuthorPortalService authorPortal;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // ---------- design queue ----------

    @Transactional(readOnly = true)
    public List<ManuscriptSummary> designQueue() {
        return queries.summaries(manuscriptRepository.findByStatusInOrderByUpdatedAtDesc(List.of(
                ManuscriptStatus.ACCEPTED, ManuscriptStatus.IN_DESIGN, ManuscriptStatus.AWAITING_AUTHOR_APPROVAL,
                ManuscriptStatus.DESIGN_APPROVED)));
    }

    @Transactional(readOnly = true)
    public List<ManuscriptSummary> productionQueue() {
        return queries.summaries(manuscriptRepository.findByStatusInOrderByUpdatedAtDesc(List.of(
                ManuscriptStatus.DESIGN_APPROVED, ManuscriptStatus.QC_PASSED, ManuscriptStatus.READY_FOR_PRINTING,
                ManuscriptStatus.PUBLISHED)));
    }

    /**
     * US16 / US17: every upload creates a new design version. Files not
     * uploaded this time are carried over from the previous version, so
     * nothing earlier is overwritten.
     */
    public BookDesign uploadVersion(Long designerId, Long manuscriptId, MultipartFile cover, MultipartFile layout,
                                    MultipartFile printFile, String notes) {
        Manuscript m = workflow.get(manuscriptId);
        workflow.requireStatus(m, "add a design version to", ManuscriptStatus.ACCEPTED, ManuscriptStatus.IN_DESIGN);
        boolean hasCover = cover != null && !cover.isEmpty();
        boolean hasLayout = layout != null && !layout.isEmpty();
        boolean hasPrint = printFile != null && !printFile.isEmpty();
        if (!hasCover && !hasLayout && !hasPrint) {
            throw new InvalidRequestException("Upload at least one file: cover image, interior layout or print-ready PDF");
        }

        BookDesign previous = designRepository.findFirstByManuscriptIdOrderByDesignVersionDesc(manuscriptId).orElse(null);
        String folder = "designs/" + manuscriptId;
        BookDesign d = new BookDesign();
        d.setManuscriptId(manuscriptId);
        d.setDesignerId(designerId);
        d.setDesignVersion(designRepository.maxVersion(manuscriptId) + 1);
        d.setCoverFilePath(hasCover ? storage.store(cover, folder, DocumentStorageService.IMAGE_TYPES, MAX_COVER_BYTES, "cover image")
                : previous == null ? null : previous.getCoverFilePath());
        d.setLayoutFilePath(hasLayout ? storage.store(layout, folder, DocumentStorageService.PDF_TYPES, MAX_PDF_BYTES, "interior layout")
                : previous == null ? null : previous.getLayoutFilePath());
        d.setPrintFilePath(hasPrint ? storage.store(printFile, folder, DocumentStorageService.PDF_TYPES, MAX_PDF_BYTES, "print-ready PDF")
                : previous == null ? null : previous.getPrintFilePath());
        d.setDesignNotes(notes == null || notes.isBlank() ? null : notes.trim());
        d.setDesignStatus(BookDesign.DRAFT);
        if (previous != null && BookDesign.DRAFT.equals(previous.getDesignStatus())) {
            previous.setDesignStatus(BookDesign.SUPERSEDED);
            designRepository.save(previous);
        }
        BookDesign saved = designRepository.save(d);
        if (ManuscriptStatus.ACCEPTED.equals(m.getStatus())) {
            workflow.changeStatus(m, ManuscriptStatus.IN_DESIGN, designerId, "Design version 1 uploaded");
        }
        return saved;
    }

    /** Sends the latest complete version to the author for approval (US18). */
    public BookDesign submitForApproval(Long designerId, Long designId) {
        BookDesign d = design(designId);
        Manuscript m = workflow.get(d.getManuscriptId());
        workflow.requireStatus(m, "send a design for approval on", ManuscriptStatus.IN_DESIGN);
        BookDesign latest = designRepository.findFirstByManuscriptIdOrderByDesignVersionDesc(m.getManuscriptId()).orElseThrow();
        if (!latest.getDesignId().equals(designId)) {
            throw new BusinessRuleException("Only the latest design version (v" + latest.getDesignVersion() + ") can be sent for approval");
        }
        if (d.getCoverFilePath() == null || d.getLayoutFilePath() == null || d.getPrintFilePath() == null) {
            throw new BusinessRuleException("A design needs a cover image, an interior layout and a print-ready PDF before the author can approve it");
        }
        d.setDesignStatus(BookDesign.SUBMITTED);
        d.setSubmittedAt(LocalDateTime.now());
        BookDesign saved = designRepository.save(d);
        workflow.changeStatus(m, ManuscriptStatus.AWAITING_AUTHOR_APPROVAL, designerId,
                "Design v" + d.getDesignVersion() + " sent to the author");
        return saved;
    }

    /** US18: the author approves or rejects the submitted design, with comments. */
    public AuthorApproval authorDecision(Long authorUserId, Long designId, boolean approve, String comments) {
        BookDesign d = design(designId);
        Manuscript m = workflow.get(d.getManuscriptId());
        Author author = authorPortal.authorFor(authorUserId);
        if (!author.getAuthorId().equals(m.getAuthorId())) {
            throw new AccessDeniedException("You can only approve designs of your own books");
        }
        if (!BookDesign.SUBMITTED.equals(d.getDesignStatus())) {
            throw new BusinessRuleException("This design version is not waiting for your approval");
        }
        if (!approve && (comments == null || comments.isBlank())) {
            throw new InvalidRequestException("Tell the designer what to change when rejecting a design");
        }
        AuthorApproval a = new AuthorApproval();
        a.setDesignId(designId);
        a.setAuthorId(author.getAuthorId());
        a.setApprovalStatus(approve ? "APPROVED" : "REJECTED");
        a.setComments(comments == null || comments.isBlank() ? null : comments.trim());
        a.setReviewedAt(LocalDateTime.now());
        AuthorApproval saved = approvalRepository.save(a);
        d.setDesignStatus(approve ? BookDesign.APPROVED : BookDesign.REJECTED);
        designRepository.save(d);
        workflow.changeStatus(m, approve ? ManuscriptStatus.DESIGN_APPROVED : ManuscriptStatus.IN_DESIGN, authorUserId,
                "Author " + (approve ? "approved" : "rejected") + " design v" + d.getDesignVersion());
        return saved;
    }

    /** Designs waiting for the logged-in author's decision. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingForAuthor(Long authorUserId) {
        Long authorId = authorPortal.authorFor(authorUserId).getAuthorId();
        return manuscriptRepository.findByAuthorIdOrderByUpdatedAtDesc(authorId).stream()
                .filter(m -> ManuscriptStatus.AWAITING_AUTHOR_APPROVAL.equals(m.getStatus()))
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("manuscript", m);
                    row.put("design", designRepository.findFirstByManuscriptIdAndDesignStatusOrderByDesignVersionDesc(
                            m.getManuscriptId(), BookDesign.SUBMITTED).orElse(null));
                    return row;
                }).toList();
    }

    /** US19: production quality check of the approved design. */
    public QualityCheck qualityCheck(Long managerId, Long manuscriptId, QualityCheckRequest r) {
        Manuscript m = workflow.get(manuscriptId);
        workflow.requireStatus(m, "quality check", ManuscriptStatus.DESIGN_APPROVED);
        for (String key : CHECKLIST.keySet()) {
            if (!r.getChecklist().containsKey(key)) {
                throw new InvalidRequestException("Answer every checklist item (missing: " + CHECKLIST.get(key) + ")");
            }
        }
        boolean passed = CHECKLIST.keySet().stream().allMatch(k -> Boolean.TRUE.equals(r.getChecklist().get(k)));
        if (!passed && (r.getNotes() == null || r.getNotes().isBlank())) {
            throw new InvalidRequestException("Add notes explaining what failed the quality check");
        }
        BookDesign approved = designRepository.findFirstByManuscriptIdAndDesignStatusOrderByDesignVersionDesc(
                manuscriptId, BookDesign.APPROVED).orElseThrow(() -> new BusinessRuleException("No approved design found"));
        QualityCheck qc = new QualityCheck();
        qc.setManuscriptId(manuscriptId);
        qc.setDesignId(approved.getDesignId());
        qc.setCheckedBy(managerId);
        qc.setResult(passed ? "PASSED" : "FAILED");
        try {
            qc.setChecklist(objectMapper.writeValueAsString(r.getChecklist()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        qc.setNotes(r.getNotes() == null || r.getNotes().isBlank() ? null : r.getNotes().trim());
        qc.setCheckedAt(LocalDateTime.now());
        QualityCheck saved = qualityCheckRepository.save(qc);
        workflow.changeStatus(m, passed ? ManuscriptStatus.QC_PASSED : ManuscriptStatus.IN_DESIGN, managerId,
                "Quality check " + (passed ? "passed" : "failed") + (qc.getNotes() == null ? "" : ": " + qc.getNotes()));
        return saved;
    }

    /** US20: creates the book record and hands it over for printing. */
    public PublishedBook markReadyForPrinting(Long managerId, Long manuscriptId, ReadyForPrintingRequest r) {
        Manuscript m = workflow.get(manuscriptId);
        workflow.requireStatus(m, "mark ready for printing", ManuscriptStatus.QC_PASSED);
        if (bookRepository.findByManuscriptId(manuscriptId).isPresent()) {
            throw new BusinessRuleException("A book already exists for manuscript " + m.getManuscriptCode());
        }
        String isbn = r.getIsbn().trim();
        if (bookRepository.existsByIsbn(isbn)) {
            throw new BusinessRuleException("ISBN " + isbn + " is already used by another book");
        }
        if (!categoryRepository.existsById(r.getCategoryId())) {
            throw new InvalidRequestException("Category #" + r.getCategoryId() + " does not exist");
        }
        BookDesign approved = designRepository.findFirstByManuscriptIdAndDesignStatusOrderByDesignVersionDesc(
                manuscriptId, BookDesign.APPROVED).orElseThrow(() -> new BusinessRuleException("No approved design found"));

        PublishedBook b = new PublishedBook();
        b.setManuscriptId(manuscriptId);
        b.setCategoryId(r.getCategoryId());
        b.setGenreId(m.getGenreId());
        b.setIsbn(isbn);
        b.setTitle(m.getTitle());
        b.setEdition(r.getEdition());
        b.setCoverImage(approved.getCoverFilePath());
        b.setPublicationDate(r.getPublicationDate());
        b.setPrice(r.getPrice());
        b.setTotalPages(r.getTotalPages());
        b.setLanguage(m.getLanguage());
        b.setBookStatus(PublishedBook.READY_FOR_PRINTING);
        PublishedBook saved = bookRepository.save(b);
        workflow.changeStatus(m, ManuscriptStatus.READY_FOR_PRINTING, managerId,
                "Ready for printing as book #" + saved.getBookId() + ", ISBN " + isbn);
        auditService.record(managerId, "BOOK_READY_FOR_PRINTING", "Book", saved.getBookId(), m.getManuscriptCode());
        return saved;
    }

    public BookDesign design(Long designId) {
        return designRepository.findById(designId)
                .orElseThrow(() -> new ResourceNotFoundException("Design not found: " + designId));
    }
}
