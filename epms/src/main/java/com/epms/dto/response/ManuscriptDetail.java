package com.epms.dto.response;

import com.epms.entity.AuthorApproval;
import com.epms.entity.BookDesign;
import com.epms.entity.EditorialReview;
import com.epms.entity.Manuscript;
import com.epms.entity.ManuscriptFile;
import com.epms.entity.ManuscriptRevision;
import com.epms.entity.ManuscriptStatusHistory;
import com.epms.entity.PublishedBook;
import com.epms.entity.QualityCheck;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Everything about one manuscript that the caller is allowed to see. */
@Data
public class ManuscriptDetail {
    private Manuscript manuscript;
    private String authorName;
    private String genreName;
    private String editorName;
    private List<ManuscriptFile> files;
    private List<ManuscriptStatusHistory> history;
    private List<EditorialReview> reviews;
    private List<ManuscriptRevision> revisions;
    private List<BookDesign> designs;
    private List<AuthorApproval> approvals;
    private List<QualityCheck> qualityChecks;
    private PublishedBook book;
    /** user id -> name, for reviewers, designers and history entries. */
    private Map<Long, String> people;
}
