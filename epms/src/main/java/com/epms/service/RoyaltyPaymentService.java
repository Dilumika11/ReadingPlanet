package com.epms.service;

import com.epms.entity.RoyaltyPayment;

import java.util.List;

/**
 * TODO (Epic 4): royalty payment workflow.
 * State machine per docs/epic-4-spec.pdf section 8:
 * PENDING -> APPROVED -> SCHEDULED -> PROCESSING -> PAID (or FAILED).
 * A payment must never reach PAID without going through approve() first,
 * and payment_reference must be unique (see entity RoyaltyPayment).
 */
public interface RoyaltyPaymentService {

    List<RoyaltyPayment> getAll();

    RoyaltyPayment getById(Long id);

    RoyaltyPayment approve(Long id, Long approvedByUserId);

    RoyaltyPayment schedule(Long id);

    RoyaltyPayment process(Long id);

    RoyaltyPayment markPaid(Long id);
}
