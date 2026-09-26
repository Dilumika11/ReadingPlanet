package com.epms.repository;

import com.epms.entity.BookstoreOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookstoreOrderItemRepository extends JpaRepository<BookstoreOrderItem, Long> {

    List<BookstoreOrderItem> findByBookstoreOrderId(Long bookstoreOrderId);
}
