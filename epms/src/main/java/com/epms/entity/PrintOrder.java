package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Epic 3: a print job. PENDING -> SCHEDULED -> IN_PROGRESS -> COMPLETED, or CANCELLED. */
@Entity
@Table(name = "print_orders")
@Data
public class PrintOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_order_id")
    private Long printOrderId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_received", nullable = false)
    private Integer quantityReceived = 0;

    @Column(name = "printing_company", length = 150)
    private String printingCompany;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING";

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
