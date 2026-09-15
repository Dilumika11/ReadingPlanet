package com.epms.service.impl;

import com.epms.entity.RoyaltyPayment;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.RoyaltyPaymentRepository;
import com.epms.service.RoyaltyPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoyaltyPaymentServiceImpl implements RoyaltyPaymentService {

    private final RoyaltyPaymentRepository royaltyPaymentRepository;

    @Override
    public List<RoyaltyPayment> getAll() {
        return royaltyPaymentRepository.findAll();
    }

    @Override
    public RoyaltyPayment getById(Long id) {
        return royaltyPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Royalty payment not found: " + id));
    }

    @Override
    public RoyaltyPayment approve(Long id, Long approvedByUserId) {

        RoyaltyPayment payment = requireStatus(id, "PENDING", "approve");

        payment.setPaymentStatus("APPROVED");
        payment.setApprovedBy(approvedByUserId);
        payment.setApprovedAt(LocalDateTime.now());

        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment schedule(Long id) {

        RoyaltyPayment payment = requireStatus(id, "APPROVED", "schedule");
        payment.setPaymentStatus("SCHEDULED");

        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment process(Long id) {

        RoyaltyPayment payment = requireStatus(id, "SCHEDULED", "process");
        payment.setPaymentStatus("PROCESSING");

        return royaltyPaymentRepository.save(payment);
    }

    @Override
    public RoyaltyPayment markPaid(Long id) {

        // Rule: a payment cannot be marked paid without having gone
        // through approval first (spec section 39, rule 10).
        RoyaltyPayment payment = requireStatus(id, "PROCESSING", "mark as paid");

        payment.setPaymentStatus("PAID");
        payment.setPaidAt(LocalDateTime.now());

        return royaltyPaymentRepository.save(payment);
    }

    private RoyaltyPayment requireStatus(Long id, String requiredStatus, String action) {

        RoyaltyPayment payment = getById(id);

        if (!requiredStatus.equalsIgnoreCase(payment.getPaymentStatus())) {
            throw new BusinessRuleException(
                    "Cannot " + action + " payment " + id + ": expected status " + requiredStatus
                            + " but was " + payment.getPaymentStatus());
        }

        return payment;
    }
}
