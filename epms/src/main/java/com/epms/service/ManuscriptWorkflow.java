package com.epms.service;

import com.epms.entity.Manuscript;
import com.epms.entity.ManuscriptStatusHistory;
import com.epms.entity.User;
import com.epms.enums.ManuscriptStatus;
import com.epms.enums.Role;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AuthorRepository;
import com.epms.repository.ManuscriptRepository;
import com.epms.repository.ManuscriptStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Shared manuscript rules used by Epics 1 and 2: status changes (always
 * recorded in the history) and who may see a manuscript.
 */
@Service
@RequiredArgsConstructor
public class ManuscriptWorkflow {

    private final ManuscriptRepository manuscriptRepository;
    private final ManuscriptStatusHistoryRepository historyRepository;
    private final AuthorRepository authorRepository;

    public Manuscript get(Long id) {
        return manuscriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manuscript not found: " + id));
    }

    /** Moves the manuscript to a new status and records who did it and why. */
    public Manuscript changeStatus(Manuscript m, String to, Long userId, String remarks) {
        String from = m.getStatus();
        m.setStatus(to);
        Manuscript saved = manuscriptRepository.save(m);
        ManuscriptStatusHistory h = new ManuscriptStatusHistory();
        h.setManuscriptId(m.getManuscriptId());
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedBy(userId);
        h.setRemarks(remarks == null ? null : (remarks.length() > 500 ? remarks.substring(0, 500) : remarks));
        historyRepository.save(h);
        return saved;
    }

    public void requireStatus(Manuscript m, String action, String... allowed) {
        if (Arrays.stream(allowed).noneMatch(a -> a.equals(m.getStatus()))) {
            throw new BusinessRuleException("Cannot " + action + " manuscript " + m.getManuscriptCode()
                    + " while it is " + m.getStatus().replace('_', ' ')
                    + " (allowed: " + String.join(", ", allowed).replace('_', ' ') + ")");
        }
    }

    /**
     * Read access: the author sees their own; the chief editor sees every
     * submitted manuscript; an editor sees the ones assigned to them;
     * designers and production see accepted ones; admin sees all (read only).
     */
    public void requireReadable(User user, Manuscript m) {
        if (!canRead(user, m)) {
            throw new AccessDeniedException("You do not have access to manuscript " + m.getManuscriptCode());
        }
    }

    public boolean canRead(User user, Manuscript m) {
        Role role = user.getRole();
        return switch (role) {
            case ADMIN -> true;
            case AUTHOR -> authorRepository.findByUserId(user.getUserId())
                    .map(a -> a.getAuthorId().equals(m.getAuthorId())).orElse(false);
            case CHIEF_EDITOR -> !ManuscriptStatus.DRAFT.equals(m.getStatus());
            case EDITOR -> user.getUserId().equals(m.getAssignedEditorId());
            case DESIGNER, PRODUCTION_MANAGER -> ManuscriptStatus.PRODUCTION.contains(m.getStatus());
            default -> false;
        };
    }

    public static List<String> editorialStatuses() {
        return ManuscriptStatus.EDITORIAL;
    }
}
