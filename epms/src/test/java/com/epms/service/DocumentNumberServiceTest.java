package com.epms.service;

import com.epms.entity.DocumentSequence;
import com.epms.repository.DocumentSequenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** US37: invoice numbers are prefix + year + 5-digit sequence, never reused. */
class DocumentNumberServiceTest {

    @Test
    void numbersIncreasePerYearAndPrefixIsApplied() {
        Map<String, DocumentSequence> table = new HashMap<>();
        DocumentSequenceRepository repo = mock(DocumentSequenceRepository.class);
        when(repo.findForUpdate(anyString())).thenAnswer(inv -> Optional.ofNullable(table.get((String) inv.getArgument(0))));
        when(repo.saveAndFlush(any(DocumentSequence.class))).thenAnswer(inv -> {
            DocumentSequence s = inv.getArgument(0);
            table.put(s.getSequenceKey(), s);
            return s;
        });
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        DocumentNumberService numbers = new DocumentNumberService(repo, tx);

        assertThat(numbers.next("INV-", "INVOICE", 2026)).isEqualTo("INV-2026-00001");
        assertThat(numbers.next("INV-", "INVOICE", 2026)).isEqualTo("INV-2026-00002");
        assertThat(numbers.next("INV-", "INVOICE", 2027)).isEqualTo("INV-2027-00001");
        assertThat(numbers.next("BILL/", "INVOICE", 2026)).isEqualTo("BILL/2026-00003");
    }
}
