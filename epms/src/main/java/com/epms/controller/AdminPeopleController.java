package com.epms.controller;

import com.epms.dto.request.RegisterRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.entity.Author;
import com.epms.entity.Manuscript;
import com.epms.entity.User;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AuthorRepository;
import com.epms.repository.ManuscriptRepository;
import com.epms.repository.UserRepository;
import com.epms.service.ManuscriptQueryService;
import com.epms.service.ManuscriptWorkflow;
import com.epms.service.UserService;
import com.epms.service.UserNames;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin oversight (read only for authors and manuscripts): US9 registered
 * authors, US10 submitted manuscripts; plus staff account management.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPeopleController {

    private final AuthorRepository authorRepository;
    private final UserRepository userRepository;
    private final ManuscriptRepository manuscriptRepository;
    private final ManuscriptQueryService queries;
    private final ManuscriptWorkflow workflow;
    private final UserService userService;
    private final UserNames names;

    @GetMapping("/authors")
    public ApiResponse<?> authors() {
        Map<Long, Long> counts = manuscriptRepository.findAll().stream()
                .collect(Collectors.groupingBy(Manuscript::getAuthorId, Collectors.counting()));
        List<Map<String, Object>> rows = authorRepository.findAll().stream().map(a -> {
            User u = userRepository.findById(a.getUserId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("author", a);
            m.put("name", names.author(a));
            m.put("email", u == null ? null : u.getEmail());
            m.put("phoneNumber", u == null ? null : u.getPhoneNumber());
            m.put("accountStatus", u == null ? null : u.getAccountStatus());
            m.put("manuscripts", counts.getOrDefault(a.getAuthorId(), 0L));
            return m;
        }).collect(Collectors.toList());
        return new ApiResponse<>(true, "Authors", rows);
    }

    @GetMapping("/authors/{id}")
    public ApiResponse<?> author(@PathVariable Long id) {
        Author a = authorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Author not found: " + id));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("author", a);
        m.put("name", names.author(a));
        m.put("user", userRepository.findById(a.getUserId()).map(AdminPeopleController::safe).orElse(null));
        m.put("manuscripts", queries.summaries(manuscriptRepository.findByAuthorIdOrderByUpdatedAtDesc(id)));
        return new ApiResponse<>(true, "Author", m);
    }

    @GetMapping("/manuscripts")
    public ApiResponse<?> manuscripts() {
        List<Manuscript> submitted = manuscriptRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(m -> !"DRAFT".equals(m.getStatus())).collect(Collectors.toList());
        return new ApiResponse<>(true, "Manuscripts", queries.summaries(submitted));
    }

    @GetMapping("/manuscripts/{id}")
    public ApiResponse<?> manuscript(@PathVariable Long id) {
        return new ApiResponse<>(true, "Manuscript", queries.detail(workflow.get(id)));
    }

    // ---------- staff accounts ----------

    @GetMapping("/users")
    public ApiResponse<?> users() {
        return new ApiResponse<>(true, "Users", userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AdminPeopleController::safe).collect(Collectors.toList()));
    }

    @PostMapping("/users")
    @Transactional
    public ApiResponse<?> createUser(@Valid @RequestBody RegisterRequest request) {
        if (request.getRole() == null) {
            throw new InvalidRequestException("Choose a role for the new account");
        }
        return userService.createUser(request);
    }

    @PostMapping("/users/{id}/status")
    @Transactional
    public ApiResponse<?> setStatus(@PathVariable Long id, @RequestParam String status) {
        String s = status.toUpperCase();
        if (!s.equals("ACTIVE") && !s.equals("INACTIVE")) {
            throw new InvalidRequestException("Status must be ACTIVE or INACTIVE");
        }
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        u.setAccountStatus(s);
        return new ApiResponse<>(true, "Account " + s.toLowerCase(), safe(userRepository.save(u)));
    }

    /** User without password hashes. */
    private static Map<String, Object> safe(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("phoneNumber", u.getPhoneNumber());
        m.put("role", u.getRole());
        m.put("accountStatus", u.getAccountStatus());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }
}
