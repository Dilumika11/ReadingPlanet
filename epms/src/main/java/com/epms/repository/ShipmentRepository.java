package com.epms.repository;

import com.epms.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByCustomerOrderId(Long customerOrderId);

    boolean existsByTrackingNumber(String trackingNumber);
}
