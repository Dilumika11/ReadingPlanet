package com.epms.entity;

import jakarta.persistence.*;
import lombok.Data;

/** Counter behind generated document numbers, one row per key (e.g. "INVOICE-2026"). */
@Entity
@Table(name = "document_sequences")
@Data
public class DocumentSequence {

    @Id
    @Column(name = "sequence_key", length = 40)
    private String sequenceKey;

    @Column(name = "next_value", nullable = false)
    private Long nextValue;
}
