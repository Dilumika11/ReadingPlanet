package com.epms.contracts;

import com.epms.contracts.dto.AuthorDto;

import java.util.List;
import java.util.Optional;

/**
 * Epic 4's only way to read Epic 1's authors. Epic 4 code never touches the
 * authors table directly, so when Epic 1 changes its storage only the
 * adapter (com.epms.adapter) changes.
 */
public interface AuthorDirectory {

    Optional<AuthorDto> findAuthor(Long authorId);

    /** The author record linked to a login account, if that user is an author. */
    Optional<AuthorDto> findByUserId(Long userId);

    List<AuthorDto> findAll();
}
