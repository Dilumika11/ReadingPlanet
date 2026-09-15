package com.epms.config;

import com.epms.dto.request.RegisterRequest;
import com.epms.enums.Role;
import com.epms.repository.UserRepository;
import com.epms.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Seeds demo staff accounts so the dashboard can be used straight after
 * startup (the dev profile's in-memory database resets on every restart).
 *
 * Same credentials scripts/demo.sh registers, so the two never collide.
 * Only runs when {@code epms.demo-data.enabled=true} (dev profile).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "epms.demo-data.enabled", havingValue = "true")
public class DemoUserInitializer implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "Passw0rd!";

    // {username, firstName, lastName, email, role}
    private static final Object[][] USERS = {
            {"demo_admin", "Demo", "Admin", "demo_admin@readingplanet.test", Role.ADMIN},
            {"demo_finance", "Demo", "Finance", "demo_finance@readingplanet.test", Role.FINANCE_STAFF},
    };

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(String... args) {
        for (Object[] u : USERS) {
            String email = (String) u[3];
            if (userRepository.existsByEmail(email)) {
                continue;
            }
            RegisterRequest request = new RegisterRequest();
            request.setUsername((String) u[0]);
            request.setFirstName((String) u[1]);
            request.setLastName((String) u[2]);
            request.setEmail(email);
            request.setPassword(DEMO_PASSWORD);
            request.setRole((Role) u[4]);
            userService.register(request);
            log.info("Demo users: seeded {} ({}) — password '{}'", email, u[4], DEMO_PASSWORD);
        }
    }
}
