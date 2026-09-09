package com.epms.security.service;

import com.epms.entity.User;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Resolves the authenticated principal (JWT subject = email) to the
 * EPMS user record. Used wherever Epic 4 endpoints need the acting
 * user's id (createdBy / approvedBy / updatedBy, audit attribution).
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + authentication.getName()));
    }

    public Long getCurrentUserId(Authentication authentication) {
        return getCurrentUser(authentication).getUserId();
    }
}
