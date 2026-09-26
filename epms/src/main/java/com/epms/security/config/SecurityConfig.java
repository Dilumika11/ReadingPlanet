package com.epms.security.config;

import com.epms.controller.GoogleLoginSuccessHandler;
import com.epms.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Stateless JWT security with role-based URL rules (Epic 4 permissions):
 * <ul>
 *   <li>Public: login/register, the public store catalogue, and the reference
 *       data other epics read (categories, genres, public settings)</li>
 *   <li>ADMIN: categories/genres/settings/announcements/books administration</li>
 *   <li>FINANCE_STAFF: every finance and royalty write</li>
 *   <li>EXECUTIVE and ADMIN: read-only access to finance, royalty and analytics</li>
 *   <li>AUTHOR: /api/author/** and /api/me/royalty/** (own data, resolved from the login)</li>
 *   <li>Epic 2/3 roles: /api/editorial, /api/design, /api/production, /api/warehouse,
 *       /api/customer and /api/sales, each limited to its roles</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String FINANCE = "FINANCE_STAFF";
    private static final String EXECUTIVE = "EXECUTIVE";
    private static final String AUTHOR = "AUTHOR";
    private static final String EDITOR = "EDITOR";
    private static final String CHIEF_EDITOR = "CHIEF_EDITOR";
    private static final String DESIGNER = "DESIGNER";
    private static final String PRODUCTION_MANAGER = "PRODUCTION_MANAGER";
    private static final String INVENTORY_STAFF = "INVENTORY_STAFF";
    private static final String SALES_STAFF = "SALES_STAFF";
    private static final String CUSTOMER = "CUSTOMER";

    private static final String[] FINANCE_PATHS = {
            "/api/finance/**",
            "/api/invoices/**",
            "/api/payments/**",
            "/api/reports/**",
            "/api/royalty-agreements/**",
            "/api/royalties/**",
            "/api/royalty-payments/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectProvider<ClientRegistrationRepository> googleClients;
    private final GoogleLoginSuccessHandler googleLoginSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                // Stateless bearer-token API: no session cookie, so no CSRF exposure.
                .csrf(csrf -> csrf.disable())

                // The API authenticates with the JWT only. A session exists solely
                // for the Google sign-in hand-shake; the login is never stored in
                // it, so a session cookie can never authorise an API call.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.IF_REQUIRED
                        )
                )
                .securityContext(c -> c.securityContextRepository(new RequestAttributeSecurityContextRepository()))

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/api/auth/**", "/api/public/**", "/oauth2/**", "/login/oauth2/**")
                        .permitAll()

                        // "Getting Published": anyone may apply and check their application
                        .requestMatchers(HttpMethod.POST, "/api/publishing-applications").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/publishing-applications/status").permitAll()
                        .requestMatchers("/api/publishing-applications/**").hasRole(ADMIN)

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                "/",
                                "/*.html",
                                "/admin/**",
                                "/author/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/**",
                                "/favicon.ico",
                                "/error",
                                "/components/**",
                                "/customer/**"
                        )
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, com.epms.controller.PublicPageController.PATHS).permitAll()

                        // Reference data Epics 2 and 3 read from Epic 4
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/genres/**", "/api/settings/public")
                        .permitAll()

                        // Current user and their own data
                        .requestMatchers("/api/me/royalty/**").hasRole(AUTHOR)
                        .requestMatchers("/api/me", "/api/me/login-activity", "/api/announcements/active").authenticated()

                        // Epic 1: an author's own profile and manuscripts
                        .requestMatchers("/api/author/**").hasRole(AUTHOR)

                        // Epic 2: editorial workflow, design and production
                        .requestMatchers("/api/editorial/overview", "/api/editorial/counts", "/api/editorial/editors",
                                "/api/editorial/manuscripts/*/assign").hasRole(CHIEF_EDITOR)
                        .requestMatchers("/api/editorial/**").hasAnyRole(EDITOR, CHIEF_EDITOR)
                        .requestMatchers("/api/design/**").hasRole(DESIGNER)
                        .requestMatchers("/api/production/**").hasAnyRole(PRODUCTION_MANAGER, ADMIN)

                        // Epic 3: warehouse, customers, wholesale sales
                        .requestMatchers("/api/warehouse/**").hasAnyRole(INVENTORY_STAFF, ADMIN)
                        .requestMatchers("/api/customer/**").hasRole(CUSTOMER)
                        .requestMatchers("/api/sales/**").hasRole(SALES_STAFF)

                        // Private files: the service checks who may read each manuscript
                        .requestMatchers("/api/documents/**").authenticated()

                        // Administration
                        .requestMatchers("/api/categories/**", "/api/genres/**", "/api/settings/**",
                                "/api/announcements/**", "/api/books/**")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/admin/dashboard").hasAnyRole(ADMIN, FINANCE, EXECUTIVE)
                        .requestMatchers("/api/admin/**").hasRole(ADMIN)

                        // Executive analytics (read-only)
                        .requestMatchers(HttpMethod.GET, "/api/analytics/**").hasAnyRole(EXECUTIVE, ADMIN, FINANCE)

                        // Finance and royalties: everyone above may read, only finance staff may change
                        .requestMatchers(HttpMethod.GET, FINANCE_PATHS).hasAnyRole(FINANCE, ADMIN, EXECUTIVE)
                        .requestMatchers(FINANCE_PATHS).hasRole(FINANCE)

                        .anyRequest()
                        .authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Please log in to continue"))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Your role does not allow this action"))
                )

                .formLogin(form -> form.disable())

                .httpBasic(httpBasic -> httpBasic.disable())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        if (googleClients.getIfAvailable() != null) {
            http.oauth2Login(o -> o.successHandler(googleLoginSuccessHandler)
                    .failureHandler((request, response, e) ->
                            response.sendRedirect("/customer-login.html?googleError=Google+sign-in+was+cancelled+or+failed")));
        }

        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
