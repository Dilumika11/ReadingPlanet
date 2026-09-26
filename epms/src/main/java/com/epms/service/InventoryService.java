package com.epms.service;

import com.epms.entity.Inventory;
import com.epms.entity.InventoryTransaction;
import com.epms.entity.PrintOrder;
import com.epms.entity.PublishedBook;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.InventoryRepository;
import com.epms.repository.InventoryTransactionRepository;
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

/**
 * Epic 3 stock (US23, US24, US27, US28). Every movement is written to
 * inventory_transactions. Available stock = in stock - reserved; orders
 * reserve under a row lock so two customers cannot buy the same last copy.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    public static final Set<String> ADJUSTMENT_TYPES = Set.of("DAMAGED", "RETURN_IN", "RETURN_OUT", "ADJUSTMENT");

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final PrintOrderRepository printOrderRepository;
    private final PublishedBookRepository bookRepository;
    private final CatalogSync catalogSync;
    private final UserNames names;

    // ---------- views (field names match the warehouse screens) ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inventory() {
        Map<Long, PublishedBook> books = books();
        return inventoryRepository.findAll().stream().map(i -> {
            PublishedBook b = books.get(i.getBookId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inventoryId", i.getInventoryId());
            m.put("bookId", i.getBookId());
            m.put("bookTitle", b == null ? "Book #" + i.getBookId() : b.getTitle());
            m.put("isbn", b == null ? null : b.getIsbn());
            m.put("quantityInStock", i.getQuantityInStock());
            m.put("quantityReserved", i.getQuantityReserved());
            m.put("available", i.getAvailable());
            m.put("reorderLevel", i.getReorderLevel());
            m.put("warehouseLocation", i.getWarehouseLocation());
            m.put("lastStockUpdate", i.getLastStockUpdate());
            m.put("lowStock", i.getAvailable() <= i.getReorderLevel());
            return m;
        }).sorted((a, b) -> String.valueOf(a.get("bookTitle")).compareToIgnoreCase(String.valueOf(b.get("bookTitle"))))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> transactions() {
        Map<Long, Long> bookByInventory = inventoryRepository.findAll().stream()
                .collect(Collectors.toMap(Inventory::getInventoryId, Inventory::getBookId));
        Map<Long, PublishedBook> books = books();
        List<InventoryTransaction> txs = transactionRepository.findAllByOrderByTransactionDateDescTransactionIdDesc();
        Map<Long, String> people = names.users(txs.stream().map(InventoryTransaction::getPerformedBy).toList());
        return txs.stream().map(t -> {
            PublishedBook b = books.get(bookByInventory.get(t.getInventoryId()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transactionId", t.getTransactionId());
            m.put("transactionDate", t.getTransactionDate());
            m.put("bookTitle", b == null ? "" : b.getTitle());
            m.put("transactionType", t.getTransactionType());
            m.put("quantity", t.getQuantity());
            m.put("referenceType", t.getReferenceType());
            m.put("referenceId", t.getReferenceId());
            m.put("remarks", t.getRemarks());
            m.put("performedByName", people.get(t.getPerformedBy()));
            return m;
        }).collect(Collectors.toList());
    }

    /** Completed print jobs that still have copies to receive (US23). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> completedPrintOrders() {
        Map<Long, PublishedBook> books = books();
        return printOrderRepository.findByStatusOrderByCreatedAtDesc("COMPLETED").stream()
                .filter(o -> o.getQuantity() - o.getQuantityReceived() > 0)
                .map(o -> PrintOrderService.view(o, books.get(o.getBookId()), null))
                .collect(Collectors.toList());
    }

    // ---------- warehouse actions ----------

    /** US23: printed copies arriving from the printer. */
    public Inventory receive(Long printOrderId, int quantity, String location, String remarks, Long userId) {
        PrintOrder order = printOrderRepository.findById(printOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Print order not found: " + printOrderId));
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessRuleException("Only completed print jobs can be received (print order #" + printOrderId
                    + " is " + order.getStatus() + ")");
        }
        int remaining = order.getQuantity() - order.getQuantityReceived();
        if (quantity < 1) {
            throw new InvalidRequestException("Quantity received must be at least 1");
        }
        if (quantity > remaining) {
            throw new BusinessRuleException("Only " + remaining + " copies of print order #" + printOrderId
                    + " are still to be received");
        }
        Inventory inv = inventoryRepository.lockByBookId(order.getBookId()).orElseGet(() -> {
            Inventory created = new Inventory();
            created.setBookId(order.getBookId());
            return inventoryRepository.saveAndFlush(created);
        });
        if (location != null && !location.isBlank()) {
            inv.setWarehouseLocation(location.trim());
        }
        inv.setQuantityInStock(inv.getQuantityInStock() + quantity);
        Inventory saved = inventoryRepository.save(inv);
        record(saved, "RECEIPT", quantity, "PRINT_ORDER", printOrderId, remarks, userId);

        order.setQuantityReceived(order.getQuantityReceived() + quantity);
        printOrderRepository.save(order);

        // First stock of a new book: it is now published and listed in the store.
        PublishedBook book = bookRepository.findById(order.getBookId()).orElseThrow();
        if (PublishedBook.READY_FOR_PRINTING.equals(book.getBookStatus())) {
            book.setBookStatus(PublishedBook.PUBLISHED);
            if (book.getPublicationDate() == null) {
                book.setPublicationDate(LocalDate.now());
            }
            bookRepository.save(book);
            catalogSync.onPublished(book, userId);
        }
        return saved;
    }

    /** US24: damaged, returned or corrected stock. */
    public Inventory adjust(Long bookId, String type, String direction, int quantity, String remarks, Long userId) {
        if (type == null || !ADJUSTMENT_TYPES.contains(type)) {
            throw new InvalidRequestException("Adjustment type must be DAMAGED, RETURN_IN, RETURN_OUT or ADJUSTMENT");
        }
        if (quantity < 1) {
            throw new InvalidRequestException("Quantity must be at least 1");
        }
        if (remarks == null || remarks.isBlank()) {
            throw new InvalidRequestException("Add remarks explaining the adjustment");
        }
        boolean in = switch (type) {
            case "RETURN_IN" -> true;
            case "ADJUSTMENT" -> {
                if (!"IN".equals(direction) && !"OUT".equals(direction)) {
                    throw new InvalidRequestException("Choose whether the adjustment adds (IN) or removes (OUT) stock");
                }
                yield "IN".equals(direction);
            }
            default -> false;
        };
        Inventory inv = inventoryRepository.lockByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("This book has no stock record yet"));
        if (!in && quantity > inv.getAvailable()) {
            throw new BusinessRuleException("Cannot remove " + quantity + " copies: only " + inv.getAvailable()
                    + " are available (" + inv.getQuantityReserved() + " are reserved for orders)");
        }
        inv.setQuantityInStock(inv.getQuantityInStock() + (in ? quantity : -quantity));
        Inventory saved = inventoryRepository.save(inv);
        String txType = "ADJUSTMENT".equals(type) ? "ADJUSTMENT_" + direction : type;
        record(saved, txType, quantity, "ADJUSTMENT", null, remarks, userId);
        return saved;
    }

    // ---------- used by orders ----------

    public void reserve(Long bookId, int quantity, String refType, Long refId, Long userId, String title) {
        Inventory inv = inventoryRepository.lockByBookId(bookId)
                .orElseThrow(() -> new BusinessRuleException("\"" + title + "\" is out of stock"));
        if (inv.getAvailable() < quantity) {
            throw new BusinessRuleException("Only " + Math.max(0, inv.getAvailable()) + " copies of \"" + title
                    + "\" are available (you asked for " + quantity + ")");
        }
        inv.setQuantityReserved(inv.getQuantityReserved() + quantity);
        record(inventoryRepository.save(inv), "RESERVED", quantity, refType, refId, null, userId);
    }

    public void release(Long bookId, int quantity, String refType, Long refId, Long userId) {
        Inventory inv = inventoryRepository.lockByBookId(bookId).orElseThrow();
        inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - quantity));
        record(inventoryRepository.save(inv), "RELEASED", quantity, refType, refId, null, userId);
    }

    /** Reserved copies leave the warehouse. */
    public void shipReserved(Long bookId, int quantity, String refType, Long refId, Long userId) {
        Inventory inv = inventoryRepository.lockByBookId(bookId).orElseThrow();
        inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - quantity));
        inv.setQuantityInStock(inv.getQuantityInStock() - quantity);
        record(inventoryRepository.save(inv), "SALE_OUT", quantity, refType, refId, null, userId);
    }

    @Transactional(readOnly = true)
    public int available(Long bookId) {
        return inventoryRepository.findByBookId(bookId).map(Inventory::getAvailable).orElse(0);
    }

    private void record(Inventory inv, String type, int qty, String refType, Long refId, String remarks, Long userId) {
        InventoryTransaction t = new InventoryTransaction();
        t.setInventoryId(inv.getInventoryId());
        t.setPerformedBy(userId);
        t.setTransactionType(type);
        t.setQuantity(qty);
        t.setReferenceType(refType);
        t.setReferenceId(refId);
        t.setRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());
        transactionRepository.save(t);
    }

    private Map<Long, PublishedBook> books() {
        return bookRepository.findAll().stream().collect(Collectors.toMap(PublishedBook::getBookId, Function.identity()));
    }
}
