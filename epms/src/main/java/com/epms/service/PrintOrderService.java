package com.epms.service;

import com.epms.entity.PrintOrder;
import com.epms.entity.PublishedBook;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.PrintOrderRepository;
import com.epms.repository.PublishedBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Epic 3 (US21, US22): print jobs for books that are ready for printing. */
@Service
@RequiredArgsConstructor
@Transactional
public class PrintOrderService {

    private static final Map<String, Set<String>> NEXT = Map.of(
            "PENDING", Set.of("PENDING", "SCHEDULED", "IN_PROGRESS", "CANCELLED"),
            "SCHEDULED", Set.of("SCHEDULED", "PENDING", "IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("IN_PROGRESS", "COMPLETED", "CANCELLED"),
            "COMPLETED", Set.of("COMPLETED"),
            "CANCELLED", Set.of("CANCELLED"));

    private final PrintOrderRepository printOrderRepository;
    private final PublishedBookRepository bookRepository;
    private final UserNames names;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> approvedBooks() {
        return bookRepository.findByBookStatusInOrderByTitle(List.of(PublishedBook.READY_FOR_PRINTING, PublishedBook.PUBLISHED))
                .stream().map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("bookId", b.getBookId());
                    m.put("title", b.getTitle());
                    m.put("isbn", b.getIsbn());
                    m.put("bookStatus", b.getBookStatus());
                    m.put("price", b.getPrice());
                    return m;
                }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Map<Long, PublishedBook> books = bookRepository.findAll().stream()
                .collect(Collectors.toMap(PublishedBook::getBookId, Function.identity()));
        List<PrintOrder> orders = printOrderRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> people = names.users(orders.stream().map(PrintOrder::getRequestedBy).toList());
        return orders.stream().map(o -> view(o, books.get(o.getBookId()), people.get(o.getRequestedBy())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long id) {
        PrintOrder o = find(id);
        return view(o, bookRepository.findById(o.getBookId()).orElse(null), names.user(o.getRequestedBy()));
    }

    public Map<String, Object> create(Long bookId, Integer quantity, String printingCompany, LocalDate expected, Long userId) {
        PublishedBook book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + bookId));
        if (!PublishedBook.READY_FOR_PRINTING.equals(book.getBookStatus()) && !PublishedBook.PUBLISHED.equals(book.getBookStatus())) {
            throw new BusinessRuleException("\"" + book.getTitle() + "\" has not been approved for printing");
        }
        validate(quantity, expected);
        PrintOrder o = new PrintOrder();
        o.setBookId(bookId);
        o.setRequestedBy(userId);
        o.setQuantity(quantity);
        o.setPrintingCompany(blank(printingCompany));
        o.setOrderDate(LocalDate.now());
        o.setExpectedCompletionDate(expected);
        o.setStatus("PENDING");
        PrintOrder saved = printOrderRepository.save(o);
        return view(saved, book, names.user(userId));
    }

    public Map<String, Object> update(Long id, Integer quantity, String printingCompany, LocalDate expected, String status) {
        PrintOrder o = find(id);
        String to = status == null || status.isBlank() ? o.getStatus() : status.toUpperCase();
        if (!NEXT.containsKey(to)) {
            throw new InvalidRequestException("Unknown status " + status);
        }
        if (!NEXT.get(o.getStatus()).contains(to)) {
            throw new BusinessRuleException("A print job cannot go from " + o.getStatus() + " to " + to);
        }
        boolean detailsEditable = "PENDING".equals(o.getStatus()) || "SCHEDULED".equals(o.getStatus());
        if (detailsEditable) {
            // An unchanged date may be in the past by now; only a newly entered one must be in the future.
            validate(quantity == null ? o.getQuantity() : quantity,
                    expected != null && expected.equals(o.getExpectedCompletionDate()) ? null : expected);
            if (quantity != null) o.setQuantity(quantity);
            o.setPrintingCompany(blank(printingCompany));
            o.setExpectedCompletionDate(expected);
        } else if (quantity != null && !quantity.equals(o.getQuantity())) {
            throw new BusinessRuleException("The quantity cannot change once printing has started");
        }
        if ("SCHEDULED".equals(to) && o.getExpectedCompletionDate() == null) {
            throw new InvalidRequestException("Set an expected completion date to schedule the job");
        }
        if ("SCHEDULED".equals(to) && o.getPrintingCompany() == null) {
            throw new InvalidRequestException("Choose the printing company to schedule the job");
        }
        if ("COMPLETED".equals(to) && !"COMPLETED".equals(o.getStatus())) {
            o.setCompletedDate(LocalDate.now());
        }
        o.setStatus(to);
        PrintOrder saved = printOrderRepository.save(o);
        return view(saved, bookRepository.findById(o.getBookId()).orElse(null), names.user(o.getRequestedBy()));
    }

    public void delete(Long id) {
        PrintOrder o = find(id);
        if (!"PENDING".equals(o.getStatus())) {
            throw new BusinessRuleException("Only PENDING print jobs can be deleted; cancel it instead");
        }
        printOrderRepository.delete(o);
    }

    static Map<String, Object> view(PrintOrder o, PublishedBook book, String requestedBy) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("printOrderId", o.getPrintOrderId());
        m.put("bookId", o.getBookId());
        m.put("bookTitle", book == null ? "Book #" + o.getBookId() : book.getTitle());
        m.put("isbn", book == null ? null : book.getIsbn());
        m.put("quantity", o.getQuantity());
        m.put("quantityReceived", o.getQuantityReceived());
        m.put("remainingToReceive", o.getQuantity() - o.getQuantityReceived());
        m.put("printingCompany", o.getPrintingCompany());
        m.put("orderDate", o.getOrderDate());
        m.put("expectedCompletionDate", o.getExpectedCompletionDate());
        m.put("completedDate", o.getCompletedDate());
        m.put("status", o.getStatus());
        m.put("requestedByName", requestedBy);
        return m;
    }

    private PrintOrder find(Long id) {
        return printOrderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Print order not found: " + id));
    }

    private static void validate(Integer quantity, LocalDate expected) {
        if (quantity == null || quantity < 1 || quantity > 100_000) {
            throw new InvalidRequestException("Quantity must be between 1 and 100000");
        }
        if (expected != null && expected.isBefore(LocalDate.now())) {
            throw new InvalidRequestException("The expected completion date cannot be in the past");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
