package com.epms.service;

import com.epms.dto.response.ManuscriptDetail;
import com.epms.dto.response.ManuscriptSummary;
import com.epms.entity.BookDesign;
import com.epms.entity.Genre;
import com.epms.entity.Manuscript;
import com.epms.entity.ManuscriptRevision;
import com.epms.entity.ManuscriptStatusHistory;
import com.epms.repository.AuthorApprovalRepository;
import com.epms.repository.BookDesignRepository;
import com.epms.repository.EditorialReviewRepository;
import com.epms.repository.GenreRepository;
import com.epms.repository.ManuscriptFileRepository;
import com.epms.repository.ManuscriptRevisionRepository;
import com.epms.repository.ManuscriptStatusHistoryRepository;
import com.epms.repository.PublishedBookRepository;
import com.epms.repository.QualityCheckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Builds manuscript lists and detail views (shared by author, editorial, design and admin screens). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManuscriptQueryService {

    private final ManuscriptFileRepository fileRepository;
    private final ManuscriptStatusHistoryRepository historyRepository;
    private final EditorialReviewRepository reviewRepository;
    private final ManuscriptRevisionRepository revisionRepository;
    private final BookDesignRepository designRepository;
    private final AuthorApprovalRepository approvalRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final PublishedBookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final UserNames names;

    public ManuscriptDetail detail(Manuscript m) {
        ManuscriptDetail d = new ManuscriptDetail();
        d.setManuscript(m);
        d.setAuthorName(names.author(m.getAuthorId()));
        d.setGenreName(genreRepository.findById(m.getGenreId()).map(Genre::getGenreName).orElse(null));
        d.setEditorName(names.user(m.getAssignedEditorId()));
        d.setFiles(fileRepository.findByManuscriptIdOrderByFileVersionDesc(m.getManuscriptId()));
        d.setHistory(historyRepository.findByManuscriptIdOrderByChangedAtAscHistoryIdAsc(m.getManuscriptId()));
        d.setReviews(reviewRepository.findByManuscriptIdOrderByReviewRoundAsc(m.getManuscriptId()));
        d.setRevisions(revisionRepository.findByManuscriptIdOrderByRevisionRoundAsc(m.getManuscriptId()));
        List<BookDesign> designs = designRepository.findByManuscriptIdOrderByDesignVersionDesc(m.getManuscriptId());
        d.setDesigns(designs);
        d.setApprovals(designs.isEmpty() ? List.of()
                : approvalRepository.findByDesignIdInOrderByReviewedAtDesc(designs.stream().map(BookDesign::getDesignId).toList()));
        d.setQualityChecks(qualityCheckRepository.findByManuscriptIdOrderByCheckedAtDesc(m.getManuscriptId()));
        d.setBook(bookRepository.findByManuscriptId(m.getManuscriptId()).orElse(null));

        Set<Long> people = new HashSet<>();
        d.getHistory().forEach(h -> people.add(h.getChangedBy()));
        d.getReviews().forEach(r -> people.add(r.getReviewerId()));
        d.getRevisions().forEach(r -> people.add(r.getReviewerId()));
        designs.forEach(x -> people.add(x.getDesignerId()));
        d.getQualityChecks().forEach(q -> people.add(q.getCheckedBy()));
        d.getFiles().forEach(f -> people.add(f.getUploadedBy()));
        d.setPeople(names.users(people));
        return d;
    }

    public List<ManuscriptSummary> summaries(Collection<Manuscript> manuscripts) {
        Map<Long, String> genres = genreRepository.findAll().stream()
                .collect(Collectors.toMap(Genre::getGenreId, Genre::getGenreName));
        Map<Long, String> editors = names.users(manuscripts.stream().map(Manuscript::getAssignedEditorId).toList());
        LocalDate today = LocalDate.now();
        return manuscripts.stream().map(m -> {
            List<ManuscriptRevision> revisions = revisionRepository.findByManuscriptIdOrderByRevisionRoundAsc(m.getManuscriptId());
            ManuscriptRevision open = revisions.stream()
                    .filter(r -> ManuscriptRevision.PENDING.equals(r.getStatus())).reduce((a, b) -> b).orElse(null);
            LocalDateTime since = historyRepository.findByManuscriptIdOrderByChangedAtAscHistoryIdAsc(m.getManuscriptId()).stream()
                    .reduce((a, b) -> b).map(ManuscriptStatusHistory::getChangedAt).orElse(m.getUpdatedAt());
            return new ManuscriptSummary(m, names.author(m.getAuthorId()), genres.get(m.getGenreId()),
                    editors.get(m.getAssignedEditorId()),
                    (int) reviewRepository.countByManuscriptId(m.getManuscriptId()), revisions.size(),
                    open == null ? null : open.getResponseDeadline(),
                    open != null && open.getResponseDeadline() != null && open.getResponseDeadline().isBefore(today),
                    since == null ? 0 : ChronoUnit.DAYS.between(since.toLocalDate(), today));
        }).collect(Collectors.toList());
    }

}
