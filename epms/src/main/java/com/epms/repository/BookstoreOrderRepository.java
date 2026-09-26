package com.epms.repository;

import com.epms.entity.BookstoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookstoreOrderRepository extends JpaRepository<BookstoreOrder, Long> {

    List<BookstoreOrder> findAllByOrderByOrderDateDesc();

    List<BookstoreOrder> findByOrderStatusOrderByOrderDateAsc(String status);
}
