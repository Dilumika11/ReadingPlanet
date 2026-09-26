package com.epms.repository;

import com.epms.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByAddedAt(Long userId);

    Optional<CartItem> findByUserIdAndCatalogBookId(Long userId, Long catalogBookId);

    void deleteByUserId(Long userId);
}
