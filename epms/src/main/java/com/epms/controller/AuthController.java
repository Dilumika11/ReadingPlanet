package com.epms.controller;

import com.epms.dto.request.LoginRequest;
import com.epms.dto.request.RegisterRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.dto.response.LoginResponse;
import com.epms.service.AccountService;
import com.epms.service.UserService;
import com.epms.service.UserService.LoginPortal;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AccountService accountService;
    private final ObjectProvider<ClientRegistrationRepository> googleClients;

    @PostMapping("/register")
    public ApiResponse<?> register(
            @Valid @RequestBody RegisterRequest request) {

        return userService.register(request);
    }

    /** Any account (kept for scripts and tools). */
    @PostMapping("/login")
    public ApiResponse<?> login(
            @Valid @RequestBody LoginRequest request) {

        return userService.login(request);
    }

    /** Staff portal: refuses author and customer accounts. */
    @PostMapping("/staff-login")
    public ApiResponse<?> staffLogin(@Valid @RequestBody LoginRequest request) {
        return userService.login(request, LoginPortal.STAFF);
    }

    /** Store and author portal: refuses staff accounts. */
    @PostMapping("/store-login")
    public ApiResponse<?> storeLogin(@Valid @RequestBody LoginRequest request) {
        return userService.login(request, LoginPortal.STORE);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<?> forgotPassword(@RequestBody Map<String, String> body) {
        accountService.forgotPassword(body.get("email"));
        return new ApiResponse<>(true,
                "If an account exists for that e-mail, a password reset link has been sent.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<?> resetPassword(@RequestBody Map<String, String> body) {
        accountService.resetPassword(body.get("token"), body.get("newPassword"), body.get("confirmPassword"));
        return new ApiResponse<>(true, "Password reset successfully. You can log in now.", null);
    }

    /** Whether Google sign-in is configured on this server (the login pages hide the button otherwise). */
    @GetMapping("/oauth2/enabled")
    public ApiResponse<?> googleEnabled() {
        return new ApiResponse<>(true, "Google sign-in", Map.of("enabled", googleClients.getIfAvailable() != null));
    }

    /** Starts Google sign-in for the author or customer portal. */
    @GetMapping("/oauth2/start")
    public void startGoogle(@RequestParam(defaultValue = "customer") String portal, HttpSession session,
                            HttpServletResponse response) throws IOException {
        if (googleClients.getIfAvailable() == null) {
            response.sendRedirect(("author".equals(portal) ? "/author-login.html" : "/customer-login.html")
                    + "?googleError=Google+sign-in+is+not+configured+on+this+server");
            return;
        }
        session.setAttribute(GoogleLoginSuccessHandler.PORTAL_ATTR, "author".equals(portal) ? "author" : "customer");
        response.sendRedirect("/oauth2/authorization/google");
    }

    /** Hands the token from a finished Google sign-in to the callback page (one time). */
    @GetMapping("/oauth2/exchange")
    public ApiResponse<?> exchange(HttpSession session) {
        Object login = session.getAttribute(GoogleLoginSuccessHandler.RESULT_ATTR);
        session.removeAttribute(GoogleLoginSuccessHandler.RESULT_ATTR);
        if (!(login instanceof LoginResponse lr)) {
            return new ApiResponse<>(false, "Google login session has expired or is invalid.", null);
        }
        return new ApiResponse<>(true, "Google login successful.", lr);
    }
}
