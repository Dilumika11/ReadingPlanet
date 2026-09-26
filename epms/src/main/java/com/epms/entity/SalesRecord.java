package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A sale record as received from Epic 3 (Sales & Distribution).
 *
 * Epic 4 does not own sales — this is the inbound copy of the operational
 * sales information described in docs/epic-4-spec.pdf section 52, kept so
 * revenue monitoring (US38) and royalty calculation (US45) have a stable,
 * read-only source.
 */
@Entity
@Table(name = "sales_records")
@Data
public class SalesRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    private Long saleId;

    // Epic 3 order reference (e.g. CO-2026-00042 / BO-2026-00007)
    @Column(name = "sale_reference", nullable = false, unique = true, length = 40)
    private String saleReference;

    // CUSTOMER (retail order) or BOOKSTORE (wholesale order)
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    // Reference to Epic 2's book, not owned here.
    @Column(name = "book_id", nullable = false)
    private Long bookId;

    // Snapshot of the title at time of sale, for display only.
    @Column(name = "book_title", nullable = false, length = 255)
    private String bookTitle;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "sale_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal saleAmount;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    // COMPLETED / CANCELLED / RETURNED — only COMPLETED counts toward revenue & royalties
    @Column(name = "status", nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }
}
