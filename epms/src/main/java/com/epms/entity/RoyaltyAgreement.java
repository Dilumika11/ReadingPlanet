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

    // Rate used for BOOKSTORE (wholesale) sales; null = same as royaltyPercentage
    @Column(name = "wholesale_royalty_percentage", precision = 5, scale = 2)
    private BigDecimal wholesaleRoyaltyPercentage;

    // NET_SALES (qty x unit price - discount) or LIST_PRICE (qty x book list price)
    @Column(name = "basis", nullable = false, length = 20)
    private String basis = "NET_SALES";

    // Advance paid to the author up front, recouped from future royalties
    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    // Running total of the advance already recouped by live calculations
    @Column(name = "advance_recouped", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceRecouped = BigDecimal.ZERO;

    // QUARTERLY / BIANNUAL / ANNUAL
    @Column(name = "payment_frequency", nullable = false, length = 20)
    private String paymentFrequency = "QUARTERLY";

    // When the author accepted the terms in the author portal
    @Column(name = "author_signed_at")
    private LocalDateTime authorSignedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

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
