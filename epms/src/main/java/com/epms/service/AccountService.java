package com.epms.service;

import com.epms.entity.PasswordResetToken;
import com.epms.entity.User;
import com.epms.entity.UserSession;
import com.epms.exception.InvalidRequestException;
import com.epms.repository.PasswordResetTokenRepository;
import com.epms.repository.UserRepository;
import com.epms.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared core account features: password reset, login activity, own profile. */
@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private static final int RESET_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${epms.app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ---------- password reset ----------

    /** Always answers the same way, so the form cannot be used to find out which e-mails have accounts. */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("Email is required");
        }
        userRepository.findByEmail(email.trim()).filter(u -> "ACTIVE".equals(u.getAccountStatus())).ifPresent(user -> {
            tokenRepository.findByUserIdAndUsedFalse(user.getUserId()).forEach(t -> {
                t.setUsed(true);
                t.setUsedAt(LocalDateTime.now());
                tokenRepository.save(t);
            });
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getUserId());
            token.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            token.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_MINUTES));
            tokenRepository.save(token);
            String link = baseUrl + "/author/reset-password.html?token=" + token.getToken();
            emailService.send(user.getEmail(), "Reading Planet - Password Reset",
                    "Hello " + user.getFirstName() + ",\n\nWe received a request to reset your Reading Planet password.\n\n"
                            + "Open this link to choose a new password:\n\n" + link + "\n\n"
                            + "The link expires in " + RESET_MINUTES + " minutes and can only be used once. "
                            + "If you did not ask for this, you can ignore this e-mail.\n\nReading Planet");
        });
    }

    public void resetPassword(String tokenValue, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new InvalidRequestException("Password must contain at least 8 characters");
        }
        if (confirmPassword != null && !confirmPassword.equals(newPassword)) {
            throw new InvalidRequestException("The passwords do not match");
        }
        PasswordResetToken token = tokenRepository.findByToken(tokenValue == null ? "" : tokenValue.trim())
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired reset link"));
        if (token.isUsed()) {
            throw new InvalidRequestException("This reset link has already been used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRequestException("This reset link has expired, request a new one");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired reset link"));
        String hash = passwordEncoder.encode(newPassword);
        user.setPassword(hash);
        user.setLegacyPassword(hash);
        userRepository.save(user);
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
    }

    // ---------- login activity ----------

    public void recordLogin(Long userId, HttpServletRequest request) {
        UserSession s = new UserSession();
        s.setUserId(userId);
        s.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
        String ip = request == null ? null : request.getHeader("X-Forwarded-For");
        if ((ip == null || ip.isBlank()) && request != null) ip = request.getRemoteAddr();
        s.setIpAddress(ip == null ? "unknown" : (ip.length() > 45 ? ip.substring(0, 45) : ip));
        String ua = request == null ? null : request.getHeader("User-Agent");
        s.setUserAgent(ua == null ? null : (ua.length() > 500 ? ua.substring(0, 500) : ua));
        LocalDateTime now = LocalDateTime.now();
        s.setLoginAt(now);
        s.setLastActivityAt(now);
        s.setExpiresAt(now.plusDays(1));
        s.setIsActive(true);
        sessionRepository.save(s);
    }

    @Transactional(readOnly = true)
    public List<UserSession> loginActivity(Long userId) {
        return sessionRepository.findTop50ByUserIdOrderByLoginAtDesc(userId);
    }

    // ---------- own profile (customers) ----------

    @Transactional(readOnly = true)
    public Map<String, Object> profile(Long userId) {
        return view(userRepository.findById(userId).orElseThrow());
    }

    public Map<String, Object> updateProfile(Long userId, String firstName, String lastName, String phone) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new InvalidRequestException("First and last name are required");
        }
        if (phone != null && !phone.isBlank() && !phone.trim().matches("^[+0-9 ()-]{7,20}$")) {
            throw new InvalidRequestException("Phone number is not valid");
        }
        User u = userRepository.findById(userId).orElseThrow();
        u.setFirstName(firstName.trim());
        u.setLastName(lastName.trim());
        u.setFullName(u.getFirstName() + " " + u.getLastName());
        u.setPhoneNumber(phone == null || phone.isBlank() ? null : phone.trim());
        return view(userRepository.save(u));
    }

    private static Map<String, Object> view(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("fullName", u.getFullName());
        m.put("phoneNumber", u.getPhoneNumber());
        m.put("role", u.getRole().name());
        return m;
    }
}
