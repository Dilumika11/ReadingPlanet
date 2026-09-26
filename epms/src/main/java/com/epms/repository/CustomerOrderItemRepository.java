package com.epms.repository;

import com.epms.entity.CustomerOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerOrderItemRepository extends JpaRepository<CustomerOrderItem, Long> {

    List<CustomerOrderItem> findByCustomerOrderId(Long customerOrderId);
}
