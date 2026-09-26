package com.epms.repository;

import com.epms.entity.PrintOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrintOrderRepository extends JpaRepository<PrintOrder, Long> {

    List<PrintOrder> findAllByOrderByCreatedAtDesc();

    List<PrintOrder> findByStatusOrderByCreatedAtDesc(String status);
}
