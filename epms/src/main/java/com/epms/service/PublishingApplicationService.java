package com.epms.service;

import com.epms.entity.PublishingApplication;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.AuthorRepository;
import com.epms.repository.PublishingApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Epic 1 "Getting Published" (from the team's merged build): anyone can
 * apply with a sample manuscript; an admin approves or rejects it and the
 * applicant is told by e-mail. An approved applicant then registers as an
 * author with the same e-mail, which links the application to the account.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PublishingApplicationService {

    private final PublishingApplicationRepository repository;
    private final AuthorRepository authorRepository;
    private final DocumentStorageService storage;
    private final EmailService emailService;
    private final AuditService auditService;

    public PublishingApplication submit(String authorName, String contact, String phone, String email,
                                        String manuscriptName, String bookType, String description, MultipartFile file) {
        if (isBlank(authorName) || isBlank(phone) || isBlank(email) || isBlank(manuscriptName) || isBlank(bookType)) {
            throw new InvalidRequestException("Please complete all required fields");
        }
        if (!email.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidRequestException("Enter a valid e-mail address");
        }
        if (!phone.trim().matches("^[+0-9 ()-]{7,20}$")) {
            throw new InvalidRequestException("Enter a valid phone number");
        }
        PublishingApplication a = new PublishingApplication();
        a.setAuthorName(authorName.trim());
        a.setContactInformation(isBlank(contact) ? null : contact.trim());
        a.setPhone(phone.trim());
        a.setEmail(email.trim().toLowerCase());
        a.setManuscriptName(manuscriptName.trim());
        a.setBookType(bookType.trim());
        a.setShortDescription(isBlank(description) ? null : description.trim());
        String path = storage.store(file, "applications", DocumentStorageService.DOCUMENT_TYPES,
                AuthorPortalService.MAX_MANUSCRIPT_BYTES, "manuscript");
        a.setFileName(file.getOriginalFilename());
        a.setFilePath(path);
        a.setFileType(DocumentStorageService.contentTypeFor(path));
        a.setFileSize(file.getSize());
        a.setStatus("PENDING");
        return repository.save(a);
    }

    /** Public status check: needs both the application number and the e-mail used. */
    @Transactional(readOnly = true)
    public Map<String, Object> status(Long applicationId, String email) {
        PublishingApplication a = repository.findByApplicationIdAndEmailIgnoreCase(applicationId, email == null ? "" : email.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Application not found. Check your application number and e-mail address."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", a.getApplicationId());
        m.put("authorName", a.getAuthorName());
        m.put("manuscriptName", a.getManuscriptName());
        m.put("status", a.getStatus());
        m.put("submittedAt", a.getSubmittedAt());
        m.put("approvedAt", a.getApprovedAt());
        m.put("rejectionReason", a.getRejectionReason());
        m.put("authorCode", a.getAuthorUserId() == null ? null
                : authorRepository.findByUserId(a.getAuthorUserId()).map(x -> x.getAuthorCode()).orElse(null));
        m.put("loginReady", "APPROVED".equals(a.getStatus()) && a.getAuthorUserId() != null);
        return m;
    }

    @Transactional(readOnly = true)
    public List<PublishingApplication> all() {
        return repository.findAllByOrderBySubmittedAtDesc();
    }

    @Transactional(readOnly = true)
    public PublishingApplication get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    public Map<String, Object> approve(Long id, Long adminId) {
        PublishingApplication a = get(id);
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessRuleException("Application #" + id + " has already been " + a.getStatus().toLowerCase());
        }
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setReviewedBy(adminId);
        a.setRejectionReason(null);
        repository.save(a);
        boolean sent = emailService.send(a.getEmail(), "Reading Planet - Publishing Application Approved",
                "Hello " + a.getAuthorName() + ",\n\nYour publishing application #" + a.getApplicationId()
                        + " (\"" + a.getManuscriptName() + "\") has been approved.\n\n"
                        + "Please register for the Author Portal with this e-mail address. You can then submit your manuscript.\n\n"
                        + "Reading Planet");
        auditService.record(adminId, "PUBLISHING_APPLICATION_APPROVED", "PublishingApplication", id, a.getEmail());
        return result(a, sent);
    }

    public Map<String, Object> reject(Long id, String reason, Long adminId) {
        if (isBlank(reason)) {
            throw new InvalidRequestException("A reason is required to reject an application");
        }
        PublishingApplication a = get(id);
        if (!"PENDING".equals(a.getStatus())) {
            throw new BusinessRuleException("Application #" + id + " has already been " + a.getStatus().toLowerCase());
        }
        a.setStatus("REJECTED");
        a.setRejectionReason(reason.trim());
        a.setReviewedBy(adminId);
        repository.save(a);
        boolean sent = emailService.send(a.getEmail(), "Reading Planet - Publishing Application Update",
                "Hello " + a.getAuthorName() + ",\n\nYour publishing application #" + a.getApplicationId()
                        + " was not approved at this time.\n\nReason: " + a.getRejectionReason() + "\n\nReading Planet");
        auditService.record(adminId, "PUBLISHING_APPLICATION_REJECTED", "PublishingApplication", id, reason.trim());
        return result(a, sent);
    }

    private static Map<String, Object> result(PublishingApplication a, boolean emailSent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("application", a);
        m.put("emailSent", emailSent);
        return m;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
