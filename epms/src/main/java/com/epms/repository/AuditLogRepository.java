package com.epms.repository;

import com.epms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityNameOrderByCreatedAtDescAuditLogIdDesc(String entityName);

    List<AuditLog> findByEntityNameAndEntityIdOrderByCreatedAtDescAuditLogIdDesc(String entityName, Long entityId);
}
