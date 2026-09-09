package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "royalty_agreements")
@Data
public class RoyaltyAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "royalty_agreement_id")
    private Long royaltyAgreementId;

    // Reference to Epic 1's author, not owned here.
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    // Reference to Epic 2's book, not owned here.
    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "agreement_number", nullable = false, unique = true, length = 30)
    private String agreementNumber;

    @Column(name = "royalty_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal royaltyPercentage;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // DRAFT -> ACTIVE -> EXPIRED
    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
