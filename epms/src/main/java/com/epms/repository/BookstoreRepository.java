package com.epms.repository;

import com.epms.entity.Bookstore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookstoreRepository extends JpaRepository<Bookstore, Long> {

    List<Bookstore> findAllByOrderByBookstoreName();

    boolean existsByEmailIgnoreCase(String email);
}
