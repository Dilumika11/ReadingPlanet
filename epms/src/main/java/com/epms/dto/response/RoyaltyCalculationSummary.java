package com.epms.dto.response;

import com.epms.entity.RoyaltyCalculation;
import lombok.AllArgsConstructor;
import lombok.Data;

/** A calculation row with the agreement, author and book it belongs to, for lists. */
@Data
@AllArgsConstructor
public class RoyaltyCalculationSummary {
    private RoyaltyCalculation calculation;
    private String agreementNumber;
    private Long authorId;
    private String authorName;
    private Long bookId;
    private String bookTitle;
}
