package com.epms.repository;

import com.epms.entity.AuthorFinanceNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthorFinanceNotificationRepository extends JpaRepository<AuthorFinanceNotification, Long> {

    List<AuthorFinanceNotification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
