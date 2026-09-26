package com.epms.service;

import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.RoyaltyCalculationLine;
import com.epms.entity.SalesRecord;
import com.epms.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US42: the royalty formula line by line, with no database involved. */
class RoyaltyCalculatorTest {

    private RoyaltyAgreement agreement;

    @BeforeEach
    void setUp() {
        agreement = new RoyaltyAgreement();
        agreement.setAgreementNumber("RA-TEST");
        agreement.setRoyaltyPercentage(new BigDecimal("10.00"));
        agreement.setBasis("NET_SALES");
    }

    @Test
    void netSalesBasisUsesAmountLessDiscount() {
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 2, "1000.00", "200.00")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);

        // (2 x 1000 - 200) x 10% = 180
        assertThat(r.grossSales()).isEqualByComparingTo("1800.00");
        assertThat(r.royaltyBase()).isEqualByComparingTo("1800.00");
        assertThat(r.grossRoyalty()).isEqualByComparingTo("180.00");
        assertThat(r.payable()).isEqualByComparingTo("180.00");
    }

    @Test
    void listPriceBasisUsesQuantityTimesListPriceIgnoringSalePrice() {
        agreement.setBasis("LIST_PRICE");
        var r = RoyaltyCalculator.calculate(agreement, new BigDecimal("1500.00"),
                List.of(sale("CUSTOMER", 2, "1000.00", "200.00")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);

        // 2 x 1500 x 10% = 300
        assertThat(r.royaltyBase()).isEqualByComparingTo("3000.00");
        assertThat(r.grossRoyalty()).isEqualByComparingTo("300.00");
    }

    @Test
    void listPriceBasisNeedsAListPrice() {
        agreement.setBasis("LIST_PRICE");
        assertThatThrownBy(() -> RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 1, "1000.00", "0")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("list price");
    }

    @Test
    void wholesaleRateAppliesOnlyToBookstoreSales() {
        agreement.setWholesaleRoyaltyPercentage(new BigDecimal("5.00"));
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 1, "1000.00", "0"), sale("BOOKSTORE", 10, "800.00", "0")),
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO);

        // retail 1000 x 10% = 100; wholesale 8000 x 5% = 400
        assertThat(r.lines()).extracting(RoyaltyCalculationLine::getRateApplied)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10"), new BigDecimal("5"));
        assertThat(r.grossRoyalty()).isEqualByComparingTo("500.00");
        assertThat(r.unitsByChannel()).containsEntry("CUSTOMER", 1).containsEntry("BOOKSTORE", 10);
    }

    @Test
    void withoutWholesaleRateBookstoreSalesUseTheStandardRate() {
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("BOOKSTORE", 10, "800.00", "0")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(r.grossRoyalty()).isEqualByComparingTo("800.00");
    }

    @Test
    void returnedSalesEarnNoRoyaltyButAreListed() {
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 3, "1000.00", "0")),
                List.of(sale("CUSTOMER", 1, "1000.00", "0")), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(r.grossRoyalty()).isEqualByComparingTo("300.00");
        assertThat(r.unitsReturned()).isEqualTo(1);
        assertThat(r.returnsAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.lines()).hasSize(2);
        assertThat(r.lines().get(1).getLineType()).isEqualTo(RoyaltyCalculationLine.RETURN);
        assertThat(r.lines().get(1).getRoyaltyAmount()).isEqualByComparingTo("0");
    }

    @Test
    void advanceIsPartlyRecoupedThenFullyRecoupedAcrossTwoPeriods() {
        agreement.setAdvanceAmount(new BigDecimal("500.00"));
        agreement.setAdvanceRecouped(BigDecimal.ZERO);

        // Period 1: royalty 300, all of it goes to the advance
        var first = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 3, "1000.00", "0")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(first.grossRoyalty()).isEqualByComparingTo("300.00");
        assertThat(first.advanceRecouped()).isEqualByComparingTo("300.00");
        assertThat(first.payable()).isEqualByComparingTo("0.00");

        // The service adds what was recouped to the agreement's running total
        agreement.setAdvanceRecouped(first.advanceRecouped());

        // Period 2: royalty 400, the remaining 200 is recouped, 200 payable
        var second = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 4, "1000.00", "0")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(second.advanceRecouped()).isEqualByComparingTo("200.00");
        assertThat(second.payable()).isEqualByComparingTo("200.00");
    }

    @Test
    void fullyRecoupedAdvanceIsNotRecoupedAgain() {
        agreement.setAdvanceAmount(new BigDecimal("500.00"));
        agreement.setAdvanceRecouped(new BigDecimal("500.00"));
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 1, "1000.00", "0")), List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(r.advanceRecouped()).isEqualByComparingTo("0.00");
        assertThat(r.payable()).isEqualByComparingTo("100.00");
    }

    @Test
    void carriedForwardAmountIsAddedToPayable() {
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 1, "1000.00", "0")), List.of(), BigDecimal.ZERO, new BigDecimal("75.50"));
        assertThat(r.carriedForwardIn()).isEqualByComparingTo("75.50");
        assertThat(r.payable()).isEqualByComparingTo("175.50");
    }

    @Test
    void deductionsReduceBaseAndRoyalty() {
        var r = RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 10, "1000.00", "0")), List.of(), new BigDecimal("1500.00"), BigDecimal.ZERO);
        assertThat(r.royaltyBase()).isEqualByComparingTo("8500.00");
        assertThat(r.grossRoyalty()).isEqualByComparingTo("850.00");

        assertThatThrownBy(() -> RoyaltyCalculator.calculate(agreement, null,
                List.of(sale("CUSTOMER", 1, "1000.00", "0")), List.of(), new BigDecimal("2000.00"), BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("exceed gross sales");
    }

    static SalesRecord sale(String channel, int qty, String unitPrice, String discount) {
        SalesRecord s = new SalesRecord();
        s.setSaleReference("S-" + channel + qty);
        s.setChannel(channel);
        s.setBookId(1L);
        s.setBookTitle("Book");
        s.setQuantity(qty);
        s.setUnitPrice(new BigDecimal(unitPrice));
        s.setSaleAmount(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(qty)));
        s.setDiscount(new BigDecimal(discount));
        s.setSaleDate(LocalDate.of(2026, 7, 10));
        s.setStatus("COMPLETED");
        return s;
    }
}
