package com.epms.service.impl;

import com.epms.dto.request.LoginRequest;
import com.epms.dto.request.RegisterRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.dto.response.LoginResponse;
import com.epms.entity.User;
import com.epms.enums.Role;
import com.epms.repository.UserRepository;
import com.epms.security.jwt.JwtService;
import com.epms.service.UserService;
import com.epms.service.UserService.LoginPortal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.epms.service.AuthorPortalService authorPortalService;
    private final com.epms.service.AccountService accountService;
    private final com.epms.repository.PublishingApplicationRepository publishingApplicationRepository;

    /** Roles anyone may sign up for; staff accounts are created by an admin. */
    public static final java.util.Set<Role> SELF_SERVICE_ROLES = java.util.EnumSet.of(Role.AUTHOR, Role.CUSTOMER);

    @Override
    public ApiResponse<?> register(RegisterRequest request) {

        if (request.getRole() == null) {
            request.setRole(Role.CUSTOMER);
        }
        if (!SELF_SERVICE_ROLES.contains(request.getRole())) {
            return new ApiResponse<>(false,
                    "You can sign up as an author or a customer. Staff accounts are created by an administrator.", null);
        }
        return createUser(request);
    }

    @Override
    public ApiResponse<?> createUser(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername().trim())) {
            return new ApiResponse<>(false, "Username already exists", null);
        }

        // Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            return new ApiResponse<>(
                    false,
                    "Email already exists",
                    null
            );
        }

        // Create new user
        User user = new User();

        user.setUsername(request.getUsername());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        // Existing database contains a required full_name column
        user.setFullName(
                request.getFirstName() + " " + request.getLastName()
        );

        user.setEmail(request.getEmail().trim().toLowerCase());

        // Hash password using BCrypt
        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        // Main password column: password_hash
        user.setPassword(encodedPassword);

        // Existing database also contains required password column
        user.setLegacyPassword(encodedPassword);

        user.setRole(request.getRole());

        user.setAccountStatus("ACTIVE");

        // Save user
        User savedUser = userRepository.save(user);

        // Authors get their Epic 1 profile straight away (US1), linked to an
        // approved "Getting Published" application with the same e-mail.
        if (savedUser.getRole() == Role.AUTHOR) {
            authorPortalService.authorFor(savedUser.getUserId());
            publishingApplicationRepository
                    .findFirstByEmailIgnoreCaseAndStatusOrderByApprovedAtDesc(savedUser.getEmail(), "APPROVED")
                    .ifPresent(app -> {
                        app.setAuthorUserId(savedUser.getUserId());
                        publishingApplicationRepository.save(app);
                    });
        }

        return new ApiResponse<>(
                true,
                "Registration successful",
                savedUser.getUserId()
        );
    }

    @Override
    public ApiResponse<?> login(LoginRequest request) {
        return doLogin(request, null);
    }

    @Override
    public ApiResponse<?> login(LoginRequest request, LoginPortal portal) {
        return doLogin(request, portal);
    }

    private ApiResponse<?> doLogin(LoginRequest request, LoginPortal portal) {

        User user = userRepository.findByEmail(request.getEmail().trim()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return new ApiResponse<>(false, "Invalid email or password", null);
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())) {
            return new ApiResponse<>(false, "Account is not active", null);
        }

        boolean selfService = SELF_SERVICE_ROLES.contains(user.getRole());
        if (portal == LoginPortal.STAFF && selfService) {
            return new ApiResponse<>(false, "This portal is for staff only. Customers and authors should use the store login.", null);
        }
        if (portal == LoginPortal.STORE && !selfService) {
            return new ApiResponse<>(false, "This is a staff account. Please use the staff login page.", null);
        }

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .roles(user.getRole().name())
                        .build();

        String token = jwtService.generateToken(userDetails);
        accountService.recordLogin(user.getUserId(), currentRequest());

        return new ApiResponse<>(true, "Login successful", LoginResponse.of(token, user));
    }

    private static jakarta.servlet.http.HttpServletRequest currentRequest() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
