package com.epms.security;

import com.epms.contracts.AuthorDirectory;
import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.AuthorDto;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.User;
import com.epms.enums.Role;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.RoyaltyCalculationRepository;
import com.epms.repository.UserRepository;
import com.epms.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-based access through the real security filter chain and JWT tokens:
 * authors only see their own statements (US47), executives are read-only on
 * finance, and each area is limited to its roles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleAccessIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired RoyaltyAgreementRepository agreementRepository;
    @Autowired RoyaltyCalculationRepository calculationRepository;
    @Autowired JwtService jwtService;

    @MockitoBean AuthorDirectory authorDirectory;
    @MockitoBean BookCatalog bookCatalog;

    private String financeToken;
    private String execToken;
    private String author1Token;
    private String author2Token;
    private Long statementId;

    @BeforeEach
    void setUp() {
        calculationRepository.deleteAll();
        agreementRepository.deleteAll();
        userRepository.deleteAll();

        User finance = user("finance", Role.FINANCE_STAFF);
        User exec = user("exec", Role.EXECUTIVE);
        User author1 = user("author1", Role.AUTHOR);
        User author2 = user("author2", Role.AUTHOR);
        financeToken = token(finance);
        execToken = token(exec);
        author1Token = token(author1);
        author2Token = token(author2);

        when(authorDirectory.findByUserId(author1.getUserId()))
                .thenReturn(Optional.of(new AuthorDto(101L, "Author One", author1.getEmail(), author1.getUserId(), "ACTIVE")));
        when(authorDirectory.findByUserId(author2.getUserId()))
                .thenReturn(Optional.of(new AuthorDto(202L, "Author Two", author2.getEmail(), author2.getUserId(), "ACTIVE")));
        when(authorDirectory.findAuthor(101L))
                .thenReturn(Optional.of(new AuthorDto(101L, "Author One", author1.getEmail(), author1.getUserId(), "ACTIVE")));
        when(authorDirectory.findAll()).thenReturn(List.of());
        when(bookCatalog.findAll()).thenReturn(List.of());

        RoyaltyAgreement a = new RoyaltyAgreement();
        a.setAuthorId(101L);
        a.setBookId(1L);
        a.setAgreementNumber("RA-T1");
        a.setRoyaltyPercentage(new BigDecimal("10.00"));
        a.setEffectiveDate(LocalDate.of(2025, 1, 1));
        a.setStatus("ACTIVE");
        a = agreementRepository.save(a);

        RoyaltyCalculation c = new RoyaltyCalculation();
        c.setRoyaltyAgreementId(a.getRoyaltyAgreementId());
        c.setSalesPeriodStart(LocalDate.of(2025, 1, 1));
        c.setSalesPeriodEnd(LocalDate.of(2025, 3, 31));
        c.setStatus("STATEMENT_ISSUED");
        c.setStatementNumber("RS-2025-00001");
        c.setPayableAmount(new BigDecimal("1200.00"));
        statementId = calculationRepository.save(c).getCalculationId();
    }

    @Test
    void authorCanReadOwnStatementButNotAnotherAuthors() throws Exception {
        mvc.perform(get("/api/me/royalty/statements/" + statementId).header("Authorization", "Bearer " + author1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statementNumber").value("RS-2025-00001"));

        mvc.perform(get("/api/me/royalty/statements/" + statementId).header("Authorization", "Bearer " + author2Token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/me/royalty/statements").header("Authorization", "Bearer " + author2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void authorCannotUseTheFinanceApi() throws Exception {
        mvc.perform(get("/api/royalties/" + statementId + "/statement").header("Authorization", "Bearer " + author1Token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/royalties/author/202").header("Authorization", "Bearer " + author1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void executiveCanReadFinanceButNotWrite() throws Exception {
        mvc.perform(get("/api/royalties").header("Authorization", "Bearer " + execToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/royalties/" + statementId + "/approve").header("Authorization", "Bearer " + execToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/finance/expenses").header("Authorization", "Bearer " + execToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"RENT\",\"amount\":10,\"expenseDate\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reports/generate?reportType=REVENUE&periodStart=2026-01-01&periodEnd=2026-01-31")
                        .header("Authorization", "Bearer " + execToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeStaffCanWriteFinanceButNotAdministerSettings() throws Exception {
        mvc.perform(post("/api/finance/expenses").header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"RENT\",\"amount\":10,\"expenseDate\":\"2026-01-01\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/settings").header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settingKey\":\"taxRatePercent\",\"settingValue\":\"5\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void referenceDataIsPublicAndEverythingElseNeedsLogin() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        mvc.perform(get("/api/settings/public")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("LKR"));
        mvc.perform(get("/api/royalties")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    private User user(String name, Role role) {
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@test.lk");
        u.setPassword("x");
        u.setLegacyPassword("x");
        u.setFirstName(name);
        u.setLastName("Test");
        u.setFullName(name + " Test");
        u.setRole(role);
        return userRepository.save(u);
    }

    private String token(User u) {
        return jwtService.generateToken(org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail()).password("x").roles(u.getRole().name()).build());
    }
}
