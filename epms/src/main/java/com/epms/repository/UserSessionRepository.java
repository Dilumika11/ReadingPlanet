package com.epms.repository;

import com.epms.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    List<UserSession> findTop50ByUserIdOrderByLoginAtDesc(Long userId);
}
