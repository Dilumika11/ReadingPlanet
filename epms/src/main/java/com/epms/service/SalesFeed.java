package com.epms.service;

import com.epms.entity.SalesRecord;
import com.epms.repository.SalesRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Epic 3 -> Epic 4 hand-over: a delivered order line becomes a COMPLETED
 * sale in sales_records, which revenue monitoring and royalty calculation
 * read. Nothing is written before delivery, so cancelled or undelivered
 * orders never count.
 */
@Service
@RequiredArgsConstructor
public class SalesFeed {

    public static final String RETAIL = "CUSTOMER";
    public static final String WHOLESALE = "BOOKSTORE";

    private final SalesRecordRepository salesRecordRepository;

    public void completedLine(String channel, String orderNumber, int lineNo, Long bookId, String title,
                              int quantity, BigDecimal unitPrice, BigDecimal discount) {
        SalesRecord s = new SalesRecord();
        s.setSaleReference(orderNumber + "-" + lineNo);
        s.setChannel(channel);
        s.setBookId(bookId);
        s.setBookTitle(title);
        s.setQuantity(quantity);
        s.setUnitPrice(unitPrice);
        s.setSaleAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        s.setDiscount(discount == null ? BigDecimal.ZERO : discount);
        s.setSaleDate(LocalDate.now());
        s.setStatus(SalesDataService.STATUS_COMPLETED);
        salesRecordRepository.save(s);
    }
}
