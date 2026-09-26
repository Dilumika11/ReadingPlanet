package com.epms.dto.response;

import com.epms.entity.RoyaltyCalculationLine;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Everything printed on a royalty statement (US44). */
@Data
public class RoyaltyStatementResponse {
    private Long calculationId;
    private String statementNumber;
    private LocalDateTime issuedAt;
    private String status;

    private String companyName;
    private String companyAddress;
    private String currency;

    private Long authorId;
    private String authorName;
    private String authorEmail;
    private Long bookId;
    private String bookTitle;
    private String isbn;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Agreement terms as used by this calculation
    private String agreementNumber;
    private String basis;
    private BigDecimal royaltyRate;
    private BigDecimal wholesaleRate;
    private String paymentFrequency;
    private BigDecimal advanceAmount;

    private Map<String, Integer> unitsByChannel;
    private int unitsSold;
    private int unitsReturned;
    private BigDecimal returnsAmount;
    private BigDecimal grossSales;
    private BigDecimal deductions;
    private BigDecimal royaltyBase;
    private BigDecimal grossRoyalty;
    private BigDecimal advanceRecouped;
    private BigDecimal carriedForwardIn;
    private BigDecimal payableAmount;

    private LocalDate paidOn;
    private String paymentMethod;
    private String paymentReference;

    private List<RoyaltyCalculationLine> lines;
}
