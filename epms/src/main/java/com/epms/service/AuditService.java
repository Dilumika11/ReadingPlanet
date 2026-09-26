package com.epms.service;

import com.epms.entity.AuditLog;
import com.epms.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Writes Epic 4 events (settings changed, royalty calculated / approved /
 * paid, report finalized, ...) to the shared audit_logs table. Joins the
 * caller's transaction, so an action and its audit entry commit together.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(Long userId, String action, String entityName, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    public List<AuditLog> history(String entityName) {
        return auditLogRepository.findByEntityNameOrderByCreatedAtDescAuditLogIdDesc(entityName);
    }

    public List<AuditLog> history(String entityName, Long entityId) {
        return auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtDescAuditLogIdDesc(entityName, entityId);
    }
}
