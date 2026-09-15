package com.epms.service;

import com.epms.dto.response.RevenueSummaryResponse;
import com.epms.dto.response.SalesSummaryResponse;
import com.epms.entity.SalesRecord;
import com.epms.exception.InvalidRequestException;
import com.epms.repository.SalesRecordRepository;
import com.epms.service.impl.SalesDataServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * US38 — revenue is built only from COMPLETED sales; cancelled and returned
 * sales are received but excluded (spec section 39 rules 1 and 12).
 */
@ExtendWith(MockitoExtension.class)
class SalesDataServiceImplTest {

    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    @Mock SalesRecordRepository repository;
    @InjectMocks SalesDataServiceImpl service;

    @Test
    void revenueCountsOnlyCompletedSalesAndReportsExclusions() {
        when(repository.findBySaleDateBetweenOrderBySaleDateDesc(FROM, TO)).thenReturn(List.of(
                sale("CO-1", "CUSTOMER", 1L, "Book A", 2, "1000.00", LocalDate.of(2026, 6, 5), "COMPLETED"),
                sale("CO-2", "CUSTOMER", 1L, "Book A", 1, "1000.00", LocalDate.of(2026, 6, 20), "CANCELLED"),
                sale("BO-1", "BOOKSTORE", 2L, "Book B", 10, "500.00", LocalDate.of(2026, 7, 2), "COMPLETED"),
                sale("CO-3", "CUSTOMER", 2L, "Book B", 1, "500.00", LocalDate.of(2026, 7, 9), "RETURNED")
        ));

        RevenueSummaryResponse rev = service.getRevenue(FROM, TO);

        assertThat(rev.getTotalRevenue()).isEqualByComparingTo("7000.00");
        assertThat(rev.getCompletedSales()).isEqualTo(2);
        assertThat(rev.getBooksSold()).isEqualTo(12);
        assertThat(rev.getAverageSaleValue()).isEqualByComparingTo("3500.00");

        assertThat(rev.getRevenueByChannel())
                .containsEntry("CUSTOMER", new BigDecimal("2000.00"))
                .containsEntry("BOOKSTORE", new BigDecimal("5000.00"));

        assertThat(rev.getMonthlyRevenue()).extracting(RevenueSummaryResponse.MonthlyRevenue::getMonth)
                .containsExactly("2026-06", "2026-07");
        assertThat(rev.getMonthlyRevenue().get(0).getRevenue()).isEqualByComparingTo("2000.00");
        assertThat(rev.getMonthlyRevenue().get(1).getRevenue()).isEqualByComparingTo("5000.00");

        assertThat(rev.getTopBooks()).hasSize(2);
        assertThat(rev.getTopBooks().get(0).getBookTitle()).isEqualTo("Book B");

        assertThat(rev.getExcluded().getCancelledCount()).isEqualTo(1);
        assertThat(rev.getExcluded().getCancelledAmount()).isEqualByComparingTo("1000.00");
        assertThat(rev.getExcluded().getReturnedCount()).isEqualTo(1);
        assertThat(rev.getExcluded().getReturnedAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void emptyPeriodStillListsEveryMonthWithZeroes() {
        when(repository.findBySaleDateBetweenOrderBySaleDateDesc(FROM, TO)).thenReturn(List.of());

        RevenueSummaryResponse rev = service.getRevenue(FROM, TO);

        assertThat(rev.getTotalRevenue()).isEqualByComparingTo("0.00");
        assertThat(rev.getAverageSaleValue()).isEqualByComparingTo("0.00");
        assertThat(rev.getMonthlyRevenue()).hasSize(2);
        assertThat(rev.getTopBooks()).isEmpty();
    }

    @Test
    void completedSalesForBookSumsQuantityAndAmount() {
        when(repository.findByBookIdAndStatusAndSaleDateBetween(1L, "COMPLETED", FROM, TO)).thenReturn(List.of(
                sale("CO-1", "CUSTOMER", 1L, "Book A", 2, "1000.00", LocalDate.of(2026, 6, 5), "COMPLETED"),
                sale("BO-1", "BOOKSTORE", 1L, "Book A", 20, "900.00", LocalDate.of(2026, 7, 1), "COMPLETED")
        ));

        SalesSummaryResponse sum = service.getCompletedSalesForBook(1L, FROM, TO);

        assertThat(sum.getCompletedSales()).isEqualTo(2);
        assertThat(sum.getBooksSold()).isEqualTo(22);
        assertThat(sum.getGrossSales()).isEqualByComparingTo("20000.00");
    }

    @Test
    void rejectsPeriodEndBeforeStart() {
        assertThatThrownBy(() -> service.getRevenue(TO, FROM))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("before period start");
        assertThatThrownBy(() -> service.getCompletedSalesForBook(1L, TO, FROM))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.getSales(TO, FROM, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsFilterStartingInTheFutureOrTooWide() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThatThrownBy(() -> service.getRevenue(tomorrow, tomorrow.plusDays(5)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("in the future");
        assertThatThrownBy(() -> service.getRevenue(LocalDate.now().minusYears(6), LocalDate.now()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("too long");
    }

    private static SalesRecord sale(String ref, String channel, Long bookId, String title, int qty,
                                    String unitPrice, LocalDate date, String status) {
        SalesRecord s = new SalesRecord();
        s.setSaleReference(ref);
        s.setChannel(channel);
        s.setBookId(bookId);
        s.setBookTitle(title);
        s.setQuantity(qty);
        s.setUnitPrice(new BigDecimal(unitPrice));
        s.setSaleAmount(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(qty)));
        s.setSaleDate(date);
        s.setStatus(status);
        return s;
    }
}
