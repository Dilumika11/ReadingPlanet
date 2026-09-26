package com.epms.service;

import com.epms.dto.request.AuthorProfileRequest;
import com.epms.dto.request.ManuscriptRequest;
import com.epms.dto.response.ManuscriptDetail;
import com.epms.dto.response.ManuscriptSummary;
import com.epms.entity.Author;
import com.epms.entity.Manuscript;
import com.epms.entity.ManuscriptFile;
import com.epms.entity.ManuscriptRevision;
import com.epms.entity.User;
import com.epms.enums.ManuscriptStatus;
import com.epms.enums.Role;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AuthorRepository;
import com.epms.repository.GenreRepository;
import com.epms.repository.ManuscriptFileRepository;
import com.epms.repository.ManuscriptRepository;
import com.epms.repository.ManuscriptRevisionRepository;
import com.epms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Epic 1 (US1 - US8): the author's own profile and manuscripts. The author
 * is always resolved from the logged-in user, never from a request value.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthorPortalService {

    public static final long MAX_MANUSCRIPT_BYTES = 20L * 1024 * 1024;

    private final AuthorRepository authorRepository;
    private final UserRepository userRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final ManuscriptFileRepository fileRepository;
    private final ManuscriptRevisionRepository revisionRepository;
    private final GenreRepository genreRepository;
    private final ManuscriptWorkflow workflow;
    private final ManuscriptQueryService queries;
    private final DocumentStorageService storage;
    private final DocumentNumberService numbers;
    private final AuditService auditService;

    // ---------- profile (US1) ----------

    /** The author record for a user; created on first use for AUTHOR accounts. */
    public Author authorFor(Long userId) {
        return authorRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
            if (user.getRole() != Role.AUTHOR) {
                throw new AccessDeniedException("Only authors have an author profile");
            }
            Author a = new Author();
            a.setUserId(userId);
            a.setAuthorCode(numbers.next("AUT-", "AUTHOR", LocalDate.now().getYear()));
            a.setStatus("ACTIVE");
            return authorRepository.save(a);
        });
    }

    @Transactional
    public Map<String, Object> getProfile(Long userId) {
        Author a = authorFor(userId);
        User u = userRepository.findById(userId).orElseThrow();
        return profileView(a, u);
    }

    public Map<String, Object> updateProfile(Long userId, AuthorProfileRequest r) {
        Author a = authorFor(userId);
        User u = userRepository.findById(userId).orElseThrow();
        u.setFirstName(r.getFirstName().trim());
        u.setLastName(r.getLastName().trim());
        u.setFullName(u.getFirstName() + " " + u.getLastName());
        u.setPhoneNumber(blank(r.getPhoneNumber()));
        userRepository.save(u);
        a.setPenName(blank(r.getPenName()));
        a.setBiography(blank(r.getBiography()));
        a.setDateOfBirth(r.getDateOfBirth());
        a.setNationality(blank(r.getNationality()));
        a.setWebsite(blank(r.getWebsite()));
        return profileView(authorRepository.save(a), u);
    }

    /** A profile is complete once biography, nationality and a phone number are filled in. */
    public static boolean isComplete(Author a, User u) {
        return a.getBiography() != null && a.getNationality() != null && u.getPhoneNumber() != null;
    }

    private Map<String, Object> profileView(Author a, User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("authorId", a.getAuthorId());
        m.put("authorCode", a.getAuthorCode());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("email", u.getEmail());
        m.put("phoneNumber", u.getPhoneNumber());
        m.put("penName", a.getPenName());
        m.put("biography", a.getBiography());
        m.put("dateOfBirth", a.getDateOfBirth());
        m.put("nationality", a.getNationality());
        m.put("website", a.getWebsite());
        m.put("status", a.getStatus());
        m.put("profileComplete", isComplete(a, u));
        return m;
    }

    // ---------- manuscripts (US2 - US7) ----------

    @Transactional(readOnly = true)
    public List<ManuscriptSummary> myManuscripts(Long userId) {
        return queries.summaries(manuscriptRepository.findByAuthorIdOrderByUpdatedAtDesc(authorFor(userId).getAuthorId()));
    }

    @Transactional(readOnly = true)
    public ManuscriptDetail myManuscript(Long userId, Long manuscriptId) {
        return queries.detail(own(userId, manuscriptId));
    }

    public Manuscript create(Long userId, ManuscriptRequest r) {
        Author a = authorFor(userId);
        requireGenre(r.getGenreId());
        Manuscript m = new Manuscript();
        m.setAuthorId(a.getAuthorId());
        m.setManuscriptCode(numbers.next("MS-", "MANUSCRIPT", LocalDate.now().getYear()));
        apply(m, r);
        m.setStatus(ManuscriptStatus.DRAFT);
        Manuscript saved = manuscriptRepository.save(m);
        workflow.changeStatus(saved, ManuscriptStatus.DRAFT, userId, "Draft created");
        return saved;
    }

    public Manuscript update(Long userId, Long manuscriptId, ManuscriptRequest r) {
        Manuscript m = own(userId, manuscriptId);
        workflow.requireStatus(m, "edit", ManuscriptStatus.DRAFT);
        requireGenre(r.getGenreId());
        apply(m, r);
        return manuscriptRepository.save(m);
    }

    public void deleteDraft(Long userId, Long manuscriptId) {
        Manuscript m = own(userId, manuscriptId);
        workflow.requireStatus(m, "delete", ManuscriptStatus.DRAFT);
        manuscriptRepository.delete(m);
    }

    /** US3: manuscript or supporting file, stored privately; each upload is a new version. */
    public ManuscriptFile uploadFile(Long userId, Long manuscriptId, MultipartFile file, String category, String notes) {
        Manuscript m = own(userId, manuscriptId);
        workflow.requireStatus(m, "upload files to", ManuscriptStatus.DRAFT);
        String cat = category == null || category.isBlank() ? ManuscriptFile.MANUSCRIPT : category.trim().toUpperCase();
        if (!ManuscriptFile.MANUSCRIPT.equals(cat) && !ManuscriptFile.SUPPORTING.equals(cat)) {
            throw new InvalidRequestException("File category must be MANUSCRIPT or SUPPORTING");
        }
        return saveFile(m, file, cat, notes, userId);
    }

    /** US2: sends the draft to the editorial team. */
    public Manuscript submit(Long userId, Long manuscriptId) {
        Manuscript m = own(userId, manuscriptId);
        workflow.requireStatus(m, "submit", ManuscriptStatus.DRAFT);
        Author a = authorFor(userId);
        User u = userRepository.findById(userId).orElseThrow();
        if (!isComplete(a, u)) {
            throw new BusinessRuleException("Complete your author profile (biography, nationality and phone number) before submitting");
        }
        if (fileRepository.countByManuscriptIdAndFileCategory(m.getManuscriptId(), ManuscriptFile.MANUSCRIPT) == 0) {
            throw new BusinessRuleException("Upload the manuscript file before submitting");
        }
        m.setSubmittedAt(LocalDateTime.now());
        Manuscript saved = workflow.changeStatus(m, ManuscriptStatus.SUBMITTED, userId, "Submitted by the author");
        auditService.record(userId, "MANUSCRIPT_SUBMITTED", "Manuscript", m.getManuscriptId(), m.getManuscriptCode());
        return saved;
    }

    /** US6: the revised manuscript in response to the open revision request. */
    public ManuscriptRevision respondToRevision(Long userId, Long manuscriptId, MultipartFile file, String notes) {
        Manuscript m = own(userId, manuscriptId);
        workflow.requireStatus(m, "upload a revision for", ManuscriptStatus.REVISION_REQUESTED);
        ManuscriptRevision revision = revisionRepository
                .findFirstByManuscriptIdAndStatusOrderByRevisionRoundDesc(m.getManuscriptId(), ManuscriptRevision.PENDING)
                .orElseThrow(() -> new BusinessRuleException("There is no open revision request for this manuscript"));
        ManuscriptFile saved = saveFile(m, file, ManuscriptFile.REVISION, notes, userId);
        revision.setStatus(ManuscriptRevision.RESPONDED);
        revision.setRespondedAt(LocalDateTime.now());
        revision.setResponseNotes(blank(notes));
        revision.setResponseFileId(saved.getManuscriptFileId());
        revisionRepository.save(revision);
        boolean late = revision.getResponseDeadline() != null && LocalDate.now().isAfter(revision.getResponseDeadline());
        workflow.changeStatus(m, ManuscriptStatus.RESUBMITTED, userId,
                "Revision round " + revision.getRevisionRound() + " uploaded" + (late ? " after the deadline" : ""));
        return revision;
    }

    private ManuscriptFile saveFile(Manuscript m, MultipartFile file, String category, String notes, Long userId) {
        String path = storage.store(file, "manuscripts/" + m.getManuscriptId(), DocumentStorageService.DOCUMENT_TYPES,
                MAX_MANUSCRIPT_BYTES, "manuscript");
        ManuscriptFile f = new ManuscriptFile();
        f.setManuscriptId(m.getManuscriptId());
        f.setFileName(file.getOriginalFilename() == null ? path : file.getOriginalFilename());
        f.setFilePath(path);
        f.setFileType(DocumentStorageService.contentTypeFor(path));
        f.setFileSize(file.getSize());
        f.setFileVersion(fileRepository.maxVersion(m.getManuscriptId()) + 1);
        f.setFileCategory(category);
        f.setUploadedBy(userId);
        f.setNotes(blank(notes));
        return fileRepository.save(f);
    }

    private Manuscript own(Long userId, Long manuscriptId) {
        Manuscript m = workflow.get(manuscriptId);
        if (!authorFor(userId).getAuthorId().equals(m.getAuthorId())) {
            throw new AccessDeniedException("You can only open your own manuscripts");
        }
        return m;
    }

    private void requireGenre(Long genreId) {
        if (!genreRepository.existsById(genreId)) {
            throw new InvalidRequestException("Genre #" + genreId + " does not exist");
        }
    }

    private static void apply(Manuscript m, ManuscriptRequest r) {
        m.setTitle(r.getTitle().trim());
        m.setGenreId(r.getGenreId());
        m.setSynopsis(blank(r.getSynopsis()));
        m.setLanguage(r.getLanguage().trim());
        m.setWordCount(r.getWordCount());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
