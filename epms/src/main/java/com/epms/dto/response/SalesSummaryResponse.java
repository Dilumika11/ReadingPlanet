package com.epms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Completed-sales totals for one book over a period — the input to a royalty calculation. */
@Data
@AllArgsConstructor
public class SalesSummaryResponse {
    private Long bookId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private long completedSales;
    private int booksSold;
    private BigDecimal grossSales;
}
