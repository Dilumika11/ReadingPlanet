package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Epic 1: where the author's royalties are paid (read by finance when paying). */
@Entity
@Table(name = "author_bank_details")
@Data
public class AuthorBankDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_detail_id")
    private Long bankDetailId;

    @Column(name = "author_id", nullable = false, unique = true)
    private Long authorId;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "branch", length = 150)
    private String branch;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
