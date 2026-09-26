package com.epms.service;

import com.epms.contracts.BookCatalog;
import com.epms.contracts.dto.BookDto;
import com.epms.entity.Author;
import com.epms.entity.AuthorBankDetails;
import com.epms.entity.AuthorFinanceNotification;
import com.epms.entity.RoyaltyAgreement;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AuthorBankDetailsRepository;
import com.epms.repository.AuthorFinanceNotificationRepository;
import com.epms.repository.AuthorRepository;
import com.epms.repository.RoyaltyAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Epic 1 US8 "Contracts & Royalties" (the team's author page), backed by
 * Epic 4: the author's contracts are their royalty agreements, which they
 * sign here; bank details are where finance pays royalties; notifications
 * tell the author when a statement is issued or a payment is made.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthorFinanceService {

    private final AuthorPortalService authorPortal;
    private final AuthorRepository authorRepository;
    private final RoyaltyAgreementRepository agreementRepository;
    private final AuthorBankDetailsRepository bankRepository;
    private final AuthorFinanceNotificationRepository notificationRepository;
    private final BookCatalog bookCatalog;

    @Transactional
    public Map<String, Object> overview(Long userId) {
        Author author = authorPortal.authorFor(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contracts", agreementRepository.findByAuthorId(author.getAuthorId()).stream()
                .filter(a -> !"EXPIRED".equals(a.getStatus()) || a.getAuthorSignedAt() != null)
                .map(this::contract).toList());
        m.put("bankDetails", bankRepository.findByAuthorId(author.getAuthorId()).orElse(null));
        m.put("notifications", notificationRepository.findByUserIdOrderByCreatedAtDesc(userId));
        return m;
    }

    public Map<String, Object> sign(Long userId, Long agreementId) {
        Author author = authorPortal.authorFor(userId);
        RoyaltyAgreement a = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement not found: " + agreementId));
        if (!a.getAuthorId().equals(author.getAuthorId())) {
            throw new AccessDeniedException("You can only sign your own agreements");
        }
        if ("EXPIRED".equals(a.getStatus())) {
            throw new BusinessRuleException("This agreement has expired");
        }
        if (a.getAuthorSignedAt() == null) {
            a.setAuthorSignedAt(LocalDateTime.now());
            agreementRepository.save(a);
        }
        return contract(a);
    }

    public AuthorBankDetails saveBankDetails(Long userId, String accountName, String bankName, String accountNumber, String branch) {
        if (blank(accountName) || blank(bankName) || blank(accountNumber)) {
            throw new InvalidRequestException("Account name, bank and account number are required");
        }
        if (!accountNumber.trim().matches("^[0-9 -]{4,30}$")) {
            throw new InvalidRequestException("Account number should contain only digits, spaces or hyphens");
        }
        Author author = authorPortal.authorFor(userId);
        AuthorBankDetails d = bankRepository.findByAuthorId(author.getAuthorId()).orElseGet(AuthorBankDetails::new);
        d.setAuthorId(author.getAuthorId());
        d.setAccountName(accountName.trim());
        d.setBankName(bankName.trim());
        d.setAccountNumber(accountNumber.trim());
        d.setBranch(blank(branch) ? null : branch.trim());
        AuthorBankDetails saved = bankRepository.save(d);
        notify(userId, "BANK_DETAILS_UPDATED", "Bank details updated",
                "Your bank details were updated. Future royalty payments will be made to " + d.getBankName() + ".");
        return saved;
    }

    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findById(notificationId).filter(n -> n.getUserId().equals(userId)).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    /** Finance view of where to pay an author. */
    @Transactional(readOnly = true)
    public AuthorBankDetails bankDetailsFor(Long authorId) {
        return bankRepository.findByAuthorId(authorId).orElse(null);
    }

    /** Royalty workflow events (statement issued, payment made) become author notifications. */
    @EventListener
    public void onAuthorNotice(AuthorNotice e) {
        authorRepository.findById(e.authorId()).ifPresent(a -> notify(a.getUserId(), e.type(), e.title(), e.message()));
    }

    private void notify(Long userId, String type, String title, String message) {
        AuthorFinanceNotification n = new AuthorFinanceNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        notificationRepository.save(n);
    }

    private Map<String, Object> contract(RoyaltyAgreement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contractId", a.getRoyaltyAgreementId());
        m.put("agreementNumber", a.getAgreementNumber());
        m.put("agreementTitle", "Publishing & Royalty Agreement " + a.getAgreementNumber());
        m.put("bookId", a.getBookId());
        m.put("manuscriptTitle", bookCatalog.findBook(a.getBookId()).map(BookDto::title).orElse("Book #" + a.getBookId()));
        m.put("royaltyRate", a.getRoyaltyPercentage());
        m.put("wholesaleRate", a.getWholesaleRoyaltyPercentage());
        m.put("basis", a.getBasis());
        m.put("advanceAmount", a.getAdvanceAmount());
        m.put("paymentFrequency", a.getPaymentFrequency());
        m.put("effectiveDate", a.getEffectiveDate());
        m.put("expiryDate", a.getExpiryDate());
        m.put("agreementStatus", a.getStatus());
        m.put("status", a.getAuthorSignedAt() != null ? "SIGNED" : "PENDING_SIGNATURE");
        m.put("signedAt", a.getAuthorSignedAt());
        m.put("issuedAt", a.getCreatedAt());
        return m;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
