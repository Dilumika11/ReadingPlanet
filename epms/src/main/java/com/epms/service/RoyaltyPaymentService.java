package com.epms.service;

import com.epms.dto.request.RoyaltyPayRequest;
import com.epms.entity.RoyaltyCalculation;
import com.epms.entity.RoyaltyPayment;

import java.util.List;

/**
 * Royalty payment workflow. A payment is created in APPROVED status when
 * its royalty calculation is approved, then (optionally SCHEDULED ->
 * PROCESSING ->) PAID. A payment can never reach PAID without that
 * approval, its bank/cheque reference must be unique, and once PAID it is
 * immutable.
 */
public interface RoyaltyPaymentService {

    List<RoyaltyPayment> getAll();

    RoyaltyPayment getById(Long id);

    /** Creates the APPROVED payment for an approved calculation. */
    RoyaltyPayment createForApprovedCalculation(RoyaltyCalculation calculation, Long approvedByUserId);

    /** Legacy step for payments created as PENDING outside the calculation workflow. */
    RoyaltyPayment approve(Long id, Long approvedByUserId);

    RoyaltyPayment schedule(Long id);

    RoyaltyPayment process(Long id);

    /**
     * Records the payment as made: the amount must equal the calculation's
     * payable amount, the reference must be unique. Marks the calculation
     * PAID and posts the payment as a ROYALTY expense so profit and loss
     * reflects it.
     */
    RoyaltyPayment markPaid(Long id, RoyaltyPayRequest request, Long userId);
}
