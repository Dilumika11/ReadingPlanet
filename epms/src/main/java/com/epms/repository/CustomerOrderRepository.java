package com.epms.repository;

import com.epms.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    List<CustomerOrder> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    List<CustomerOrder> findByOrderStatusInOrderByOrderDateAsc(Collection<String> statuses);

    List<CustomerOrder> findAllByOrderByOrderDateDesc();
}
