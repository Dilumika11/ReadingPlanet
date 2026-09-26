package com.epms.service;

import com.epms.entity.DocumentSequence;
import com.epms.repository.DocumentSequenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generates document numbers (invoices, royalty statements, royalty
 * payments) from a locked counter row per key and year.
 *
 * Each number is taken in its own committed transaction: if the business
 * transaction that asked for it later rolls back, the number is skipped
 * rather than handed out again, so a number is never reused.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository sequenceRepository;
    private final TransactionTemplate newTransaction;

    public DocumentNumberService(DocumentSequenceRepository sequenceRepository,
                                 PlatformTransactionManager transactionManager) {
        this.sequenceRepository = sequenceRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** e.g. format("INV-", "INVOICE", 2026) -> "INV-2026-00042". */
    public String next(String prefix, String sequenceName, int year) {
        long value = nextValue(sequenceName + "-" + year);
        return prefix + year + "-" + String.format("%05d", value);
    }

    long nextValue(String key) {
        try {
            return increment(key);
        } catch (DataIntegrityViolationException firstUseRace) {
            // Two requests created the counter row at the same moment; the row exists now.
            return increment(key);
        }
    }

    private long increment(String key) {
        Long value = newTransaction.execute(status -> {
            DocumentSequence seq = sequenceRepository.findForUpdate(key).orElseGet(() -> {
                DocumentSequence created = new DocumentSequence();
                created.setSequenceKey(key);
                created.setNextValue(1L);
                return created;
            });
            long current = seq.getNextValue();
            seq.setNextValue(current + 1);
            sequenceRepository.saveAndFlush(seq);
            return current;
        });
        return value == null ? 1L : value;
    }
}
