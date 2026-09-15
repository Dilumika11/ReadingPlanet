package com.epms.config;

import com.epms.config.DemoCatalog.DemoBook;
import com.epms.entity.RoyaltyAgreement;
import com.epms.entity.SalesRecord;
import com.epms.repository.RoyaltyAgreementRepository;
import com.epms.repository.SalesRecordRepository;
import com.epms.service.SalesDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds dummy Epic 3 sales data (and matching ACTIVE royalty agreements) so
 * revenue monitoring (US38) and royalty calculation (US45) can be demonstrated
 * before Epic 3 exists.
 *
 * Only runs when {@code epms.demo-data.enabled=true} (set in the dev profile)
 * and only when the sales table is empty, so it never touches real data.
 * Generation is deterministic (fixed seed) so demo figures are stable between
 * restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "epms.demo-data.enabled", havingValue = "true")
public class DemoSalesDataInitializer implements CommandLineRunner {

    private static final int MONTHS_OF_HISTORY = 12;

    // The dummy Epic 2 catalogue lives in DemoCatalog (shared with the store).
    private static final List<DemoBook> BOOKS = DemoCatalog.BOOKS;

    private final SalesRecordRepository salesRecordRepository;
    private final RoyaltyAgreementRepository royaltyAgreementRepository;

    @Override
    public void run(String... args) {
        if (salesRecordRepository.count() > 0) {
            log.info("Demo sales data: sales_records already populated, skipping seed");
            return;
        }

        Random random = new Random(20260101L);
        LocalDate firstMonth = LocalDate.now().withDayOfMonth(1).minusMonths(MONTHS_OF_HISTORY - 1);
        LocalDate today = LocalDate.now();

        List<SalesRecord> sales = new ArrayList<>();
        int customerSeq = 0;
        int bookstoreSeq = 0;

        for (int m = 0; m < MONTHS_OF_HISTORY; m++) {
            LocalDate monthStart = firstMonth.plusMonths(m);

            for (DemoBook book : BOOKS) {
                // Retail: 2-5 customer orders per book per month, 1-3 copies each
                int customerOrders = 2 + random.nextInt(4);
                for (int i = 0; i < customerOrders; i++) {
                    LocalDate date = randomDateIn(monthStart, today, random);
                    if (date == null) continue;
                    sales.add(record("CO-" + date.getYear() + "-" + String.format("%05d", ++customerSeq),
                            "CUSTOMER", book, 1 + random.nextInt(3), date, pickStatus(random)));
                }

                // Wholesale: 0-1 bookstore order per book per month, 10-40 copies
                if (random.nextInt(10) < 6) {
                    LocalDate date = randomDateIn(monthStart, today, random);
                    if (date != null) {
                        sales.add(record("BO-" + date.getYear() + "-" + String.format("%05d", ++bookstoreSeq),
                                "BOOKSTORE", book, 10 + random.nextInt(31), date, pickStatus(random)));
                    }
                }
            }
        }

        salesRecordRepository.saveAll(sales);
        log.info("Demo sales data: seeded {} dummy Epic 3 sales records across {} months", sales.size(), MONTHS_OF_HISTORY);

        seedAgreements(firstMonth);
    }

    private void seedAgreements(LocalDate effectiveDate) {
        if (royaltyAgreementRepository.count() > 0) {
            log.info("Demo sales data: royalty_agreements already populated, skipping agreement seed");
            return;
        }

        int seq = 0;
        for (DemoBook book : BOOKS) {
            RoyaltyAgreement agreement = new RoyaltyAgreement();
            agreement.setBookId(book.bookId());
            agreement.setAuthorId(book.authorId());
            agreement.setAgreementNumber("RA-DEMO-" + String.format("%03d", ++seq));
            agreement.setRoyaltyPercentage(new BigDecimal(seq % 2 == 0 ? "12.50" : "10.00"));
            agreement.setEffectiveDate(effectiveDate);
            agreement.setStatus("ACTIVE");
            royaltyAgreementRepository.save(agreement);
        }
        log.info("Demo sales data: seeded {} ACTIVE royalty agreements for the dummy catalogue", seq);
    }

    private static SalesRecord record(String reference, String channel, DemoBook book,
                                      int quantity, LocalDate date, String status) {
        BigDecimal unitPrice = book.price();
        SalesRecord s = new SalesRecord();
        s.setSaleReference(reference);
        s.setChannel(channel);
        s.setBookId(book.bookId());
        s.setBookTitle(book.title());
        s.setQuantity(quantity);
        s.setUnitPrice(unitPrice);
        s.setSaleAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        s.setSaleDate(date);
        s.setStatus(status);
        return s;
    }

    // ~8% cancelled, ~4% returned — enough to show they are excluded from revenue
    private static String pickStatus(Random random) {
        int roll = random.nextInt(100);
        if (roll < 8) return SalesDataService.STATUS_CANCELLED;
        if (roll < 12) return SalesDataService.STATUS_RETURNED;
        return SalesDataService.STATUS_COMPLETED;
    }

    /** A random day inside the month, never in the future; null if the month has no past days. */
    private static LocalDate randomDateIn(LocalDate monthStart, LocalDate today, Random random) {
        int lastDay = monthStart.lengthOfMonth();
        LocalDate candidate = monthStart.withDayOfMonth(1 + random.nextInt(lastDay));
        if (candidate.isAfter(today)) {
            if (monthStart.isAfter(today)) return null;
            candidate = monthStart.withDayOfMonth(1 + random.nextInt(today.getDayOfMonth()));
        }
        return candidate;
    }
}
