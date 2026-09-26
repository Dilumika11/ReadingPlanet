package com.epms.contracts.dto;

/** Minimum author fields Epic 4 needs from Epic 1 (see docs/INTEGRATION_CONTRACT.md). */
public record AuthorDto(Long authorId, String fullName, String email, Long userId, String status) {
}
