package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Epic 3: stock of one book. Available = in stock - reserved by confirmed orders. */
@Entity
@Table(name = "inventory")
@Data
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long inventoryId;

    @Column(name = "book_id", nullable = false, unique = true)
    private Long bookId;

    @Column(name = "quantity_in_stock", nullable = false)
    private Integer quantityInStock = 0;

    @Column(name = "quantity_reserved", nullable = false)
    private Integer quantityReserved = 0;

    @Column(name = "reorder_level", nullable = false)
    private Integer reorderLevel = 10;

    @Column(name = "warehouse_location", length = 100)
    private String warehouseLocation;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "last_stock_update", nullable = false)
    private LocalDateTime lastStockUpdate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public int getAvailable() {
        return quantityInStock - quantityReserved;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastStockUpdate = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        lastStockUpdate = LocalDateTime.now();
    }
}
