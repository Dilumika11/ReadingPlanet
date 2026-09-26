package com.epms.repository;

import com.epms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    java.util.List<User> findByRoleInOrderByFullName(java.util.Collection<com.epms.enums.Role> roles);

    java.util.List<User> findAllByOrderByCreatedAtDesc();
}