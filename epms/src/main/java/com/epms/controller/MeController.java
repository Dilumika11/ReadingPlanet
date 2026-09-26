package com.epms.controller;

import com.epms.contracts.AuthorDirectory;
import com.epms.contracts.dto.AuthorDto;
import com.epms.dto.response.ApiResponse;
import com.epms.entity.User;
import com.epms.enums.Role;
import com.epms.security.service.CurrentUserService;
import com.epms.service.RoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The logged-in user's own data. The author is always resolved from the
 * login (JWT), never from a request parameter, so an author can only ever
 * see their own royalties (US47).
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final CurrentUserService currentUserService;
    private final AuthorDirectory authorDirectory;
    private final RoyaltyService royaltyService;

    @GetMapping
    public ApiResponse<?> me(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("userId", user.getUserId());
        me.put("fullName", user.getFullName());
        me.put("email", user.getEmail());
        me.put("role", user.getRole());
        me.put("roles", List.of(user.getRole().name()));
        if (user.getRole() == Role.AUTHOR) {
            me.put("authorId", authorDirectory.findByUserId(user.getUserId()).map(AuthorDto::authorId).orElse(null));
        }
        return new ApiResponse<>(true, "Current user", me);
    }

    @GetMapping("/royalty/agreements")
    public ApiResponse<?> myAgreements(Authentication authentication) {
        return new ApiResponse<>(true, "Your royalty agreements",
                royaltyService.getMyAgreements(currentUserService.getCurrentUserId(authentication)));
    }

    @GetMapping("/royalty/statements")
    public ApiResponse<?> myStatements(Authentication authentication) {
        return new ApiResponse<>(true, "Your royalty statements",
                royaltyService.getMyStatements(currentUserService.getCurrentUserId(authentication)));
    }

    @GetMapping("/royalty/statements/{id}")
    public ApiResponse<?> myStatement(@PathVariable Long id, Authentication authentication) {
        return new ApiResponse<>(true, "Your royalty statement",
                royaltyService.getMyStatement(currentUserService.getCurrentUserId(authentication), id));
    }

    @GetMapping("/royalty/payments")
    public ApiResponse<?> myPayments(Authentication authentication) {
        return new ApiResponse<>(true, "Your royalty payments",
                royaltyService.getMyPayments(currentUserService.getCurrentUserId(authentication)));
    }
}
