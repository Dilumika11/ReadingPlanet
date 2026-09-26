package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Epic 3: delivery of a customer order. IN_TRANSIT -> DELIVERED. */
@Entity
@Table(name = "shipments")
@Data
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "customer_order_id", nullable = false)
    private Long customerOrderId;

    @Column(name = "tracking_number", unique = true, length = 100)
    private String trackingNumber;

    @Column(name = "shipping_provider", length = 100)
    private String shippingProvider;

    @Column(name = "shipped_date")
    private LocalDate shippedDate;

    @Column(name = "delivered_date")
    private LocalDate deliveredDate;

    @Column(name = "shipment_status", nullable = false, length = 30)
    private String shipmentStatus = "IN_TRANSIT";

    @Column(name = "shipping_cost", precision = 12, scale = 2)
    private BigDecimal shippingCost;

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
