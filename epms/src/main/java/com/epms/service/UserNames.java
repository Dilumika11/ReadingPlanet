package com.epms.service;

import com.epms.entity.Author;
import com.epms.entity.User;
import com.epms.repository.AuthorRepository;
import com.epms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Display names for user and author ids shown in lists. */
@Service
@RequiredArgsConstructor
public class UserNames {

    private final UserRepository userRepository;
    private final AuthorRepository authorRepository;

    public String user(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getFullName).orElse("User #" + userId);
    }

    public Map<Long, String> users(Collection<Long> userIds) {
        Map<Long, String> out = new HashMap<>();
        userRepository.findAllById(userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(u -> out.put(u.getUserId(), u.getFullName()));
        return out;
    }

    /** Pen name if the author set one, else the account's full name. */
    public String author(Long authorId) {
        if (authorId == null) return null;
        return authorRepository.findById(authorId).map(this::author).orElse("Author #" + authorId);
    }

    public String author(Author a) {
        if (a.getPenName() != null && !a.getPenName().isBlank()) return a.getPenName();
        return user(a.getUserId());
    }
}
