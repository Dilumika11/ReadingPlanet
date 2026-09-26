package com.epms.repository;

import com.epms.entity.PublishingApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublishingApplicationRepository extends JpaRepository<PublishingApplication, Long> {

    List<PublishingApplication> findAllByOrderBySubmittedAtDesc();

    Optional<PublishingApplication> findByApplicationIdAndEmailIgnoreCase(Long applicationId, String email);

    Optional<PublishingApplication> findFirstByEmailIgnoreCaseAndStatusOrderByApprovedAtDesc(String email, String status);
}
