package com.epms.controller;

import com.epms.dto.response.LoginResponse;
import com.epms.entity.User;
import com.epms.enums.Role;
import com.epms.repository.UserRepository;
import com.epms.security.jwt.JwtService;
import com.epms.service.AccountService;
import com.epms.service.AuthorPortalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Finishes Google sign-in (from the team's merged build): finds or creates
 * the author/customer account for the verified Google e-mail, issues the
 * usual JWT and parks it in the session for /api/auth/oauth2/exchange.
 * Staff accounts cannot sign in with Google. The Google login itself is not
 * kept as a session login: the API still only accepts the JWT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    static final String PORTAL_ATTR = "EPMS_OAUTH_PORTAL";
    static final String RESULT_ATTR = "EPMS_GOOGLE_LOGIN";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthorPortalService authorPortalService;
    private final AccountService accountService;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        HttpSession session = request.getSession(true);
        String portal = String.valueOf(session.getAttribute(PORTAL_ATTR));
        boolean authorPortal = "author".equals(portal);
        SecurityContextHolder.clearContext();
        try {
            OAuth2User g = (OAuth2User) authentication.getPrincipal();
            String email = g.getAttribute("email");
            Boolean verified = g.getAttribute("email_verified");
            if (email == null || email.isBlank() || Boolean.FALSE.equals(verified)) {
                fail(response, authorPortal, "Your Google account has no verified e-mail address.");
                return;
            }
            email = email.trim().toLowerCase();
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new User();
                String first = g.getAttribute("given_name");
                String last = g.getAttribute("family_name");
                user.setUsername("google_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                user.setEmail(email);
                user.setFirstName(first == null || first.isBlank() ? "Reader" : first);
                user.setLastName(last == null || last.isBlank() ? "Google" : last);
                user.setFullName(user.getFirstName() + " " + user.getLastName());
                String hash = passwordEncoder.encode(UUID.randomUUID().toString());
                user.setPassword(hash);
                user.setLegacyPassword(hash);
                user.setRole(authorPortal ? Role.AUTHOR : Role.CUSTOMER);
                user.setAccountStatus("ACTIVE");
                user = userRepository.save(user);
            }
            if (user.getRole() != Role.AUTHOR && user.getRole() != Role.CUSTOMER) {
                fail(response, authorPortal, "Staff accounts cannot use Google sign-in. Please use the staff login.");
                return;
            }
            if (authorPortal && user.getRole() != Role.AUTHOR) {
                fail(response, true, "This Google account is registered as a customer, not an author.");
                return;
            }
            if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
                fail(response, authorPortal, "This account is not active.");
                return;
            }
            if (user.getRole() == Role.AUTHOR) {
                authorPortalService.authorFor(user.getUserId());
            }
            String token = jwtService.generateToken(org.springframework.security.core.userdetails.User
                    .withUsername(user.getEmail()).password(user.getPassword()).roles(user.getRole().name()).build());
            accountService.recordLogin(user.getUserId(), request);
            session.setAttribute(RESULT_ATTR, LoginResponse.of(token, user));
            session.removeAttribute(PORTAL_ATTR);
            response.sendRedirect(authorPortal ? "/author/oauth2-callback.html" : "/customer/oauth2-callback.html");
        } catch (RuntimeException e) {
            log.warn("Google sign-in failed: {}", e.getMessage());
            fail(response, authorPortal, "Google sign-in failed. Please try again.");
        }
    }

    private static void fail(HttpServletResponse response, boolean authorPortal, String message) throws IOException {
        response.sendRedirect((authorPortal ? "/author-login.html" : "/customer-login.html")
                + "?googleError=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }
}
