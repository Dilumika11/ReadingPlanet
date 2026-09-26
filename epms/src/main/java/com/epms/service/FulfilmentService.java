package com.epms.service;

import com.epms.dto.request.BookstoreOrderRequest;
import com.epms.dto.request.BookstoreRequest;
import com.epms.entity.Bookstore;
import com.epms.entity.BookstoreOrder;
import com.epms.entity.BookstoreOrderItem;
import com.epms.entity.CustomerOrder;
import com.epms.entity.CustomerOrderItem;
import com.epms.entity.PublishedBook;
import com.epms.entity.Shipment;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.BookstoreOrderItemRepository;
import com.epms.repository.BookstoreOrderRepository;
import com.epms.repository.BookstoreRepository;
import com.epms.repository.CustomerOrderItemRepository;
import com.epms.repository.CustomerOrderRepository;
import com.epms.repository.PublishedBookRepository;
import com.epms.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Epic 3: warehouse fulfilment of customer orders (US28) and wholesale
 * bookstore orders with sales-manager approval (US30). Delivered lines are
 * passed to Epic 4 as completed sales.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FulfilmentService {

    private static final BigDecimal TRADE_PRICE_FACTOR = new BigDecimal("0.75");

    private final CustomerOrderRepository customerOrderRepository;
    private final CustomerOrderItemRepository customerItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreOrderRepository bookstoreOrderRepository;
    private final BookstoreOrderItemRepository bookstoreItemRepository;
    private final PublishedBookRepository bookRepository;
    private final InventoryService inventoryService;
    private final StoreService storeService;
    private final SalesFeed salesFeed;
    private final DocumentNumberService numbers;
    private final UserNames names;
    private final AuditService auditService;

    // ===================== customer orders (US28) =====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> customerOrders(String status) {
        List<CustomerOrder> orders = status == null || status.isBlank()
                ? customerOrderRepository.findAllByOrderByOrderDateDesc()
                : customerOrderRepository.findByOrderStatusInOrderByOrderDateAsc(List.of(status.toUpperCase()));
        return orders.stream().map(storeService::orderView).toList();
    }

    public Map<String, Object> pack(Long orderId, Long userId) {
        CustomerOrder o = customerOrder(orderId);
        requireStatus(o.getOrderStatus(), CustomerOrder.CONFIRMED, "pack", o.getOrderNumber());
        o.setOrderStatus(CustomerOrder.PACKED);
        o.setPackedAt(LocalDateTime.now());
        return storeService.orderView(customerOrderRepository.save(o));
    }

    public Map<String, Object> dispatch(Long orderId, String provider, String trackingNumber, BigDecimal cost, Long userId) {
        CustomerOrder o = customerOrder(orderId);
        requireStatus(o.getOrderStatus(), CustomerOrder.PACKED, "dispatch", o.getOrderNumber());
        if (provider == null || provider.isBlank()) {
            throw new InvalidRequestException("Enter the courier / shipping provider");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new InvalidRequestException("Enter the tracking number");
        }
        if (shipmentRepository.existsByTrackingNumber(trackingNumber.trim())) {
            throw new BusinessRuleException("Tracking number " + trackingNumber.trim() + " is already used by another shipment");
        }
        for (CustomerOrderItem i : customerItemRepository.findByCustomerOrderId(orderId)) {
            inventoryService.shipReserved(i.getBookId(), i.getQuantity(), "CUSTOMER_ORDER", orderId, userId);
        }
        Shipment s = new Shipment();
        s.setCustomerOrderId(orderId);
        s.setShippingProvider(provider.trim());
        s.setTrackingNumber(trackingNumber.trim());
        s.setShippingCost(cost);
        s.setShippedDate(LocalDate.now());
        s.setShipmentStatus("IN_TRANSIT");
        shipmentRepository.save(s);
        o.setOrderStatus(CustomerOrder.DISPATCHED);
        o.setDispatchedAt(LocalDateTime.now());
        return storeService.orderView(customerOrderRepository.save(o));
    }

    public Map<String, Object> deliver(Long orderId, Long userId) {
        CustomerOrder o = customerOrder(orderId);
        requireStatus(o.getOrderStatus(), CustomerOrder.DISPATCHED, "mark delivered", o.getOrderNumber());
        Shipment s = shipmentRepository.findByCustomerOrderId(orderId).orElseThrow();
        s.setShipmentStatus("DELIVERED");
        s.setDeliveredDate(LocalDate.now());
        shipmentRepository.save(s);
        o.setOrderStatus(CustomerOrder.DELIVERED);
        o.setDeliveredAt(LocalDateTime.now());
        o.setPaymentStatus("PAID");
        CustomerOrder saved = customerOrderRepository.save(o);
        Map<Long, String> titles = titles();
        int line = 1;
        for (CustomerOrderItem i : customerItemRepository.findByCustomerOrderId(orderId)) {
            salesFeed.completedLine(SalesFeed.RETAIL, o.getOrderNumber(), line++, i.getBookId(),
                    titles.getOrDefault(i.getBookId(), "Book #" + i.getBookId()), i.getQuantity(), i.getUnitPrice(), BigDecimal.ZERO);
        }
        return storeService.orderView(saved);
    }

    private CustomerOrder customerOrder(Long id) {
        return customerOrderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    // ===================== bookstores and wholesale orders (US30) =====================

    @Transactional(readOnly = true)
    public List<Bookstore> bookstores() {
        return bookstoreRepository.findAllByOrderByBookstoreName();
    }

    public Bookstore saveBookstore(Long id, BookstoreRequest r) {
        Bookstore b = id == null ? new Bookstore() : bookstoreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bookstore not found: " + id));
        String email = r.getEmail() == null || r.getEmail().isBlank() ? null : r.getEmail().trim();
        if (email != null && !email.equalsIgnoreCase(b.getEmail()) && bookstoreRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Another bookstore already uses " + email);
        }
        b.setBookstoreName(r.getBookstoreName().trim());
        b.setContactPerson(blank(r.getContactPerson()));
        b.setEmail(email);
        b.setPhoneNumber(blank(r.getPhoneNumber()));
        b.setAddress(blank(r.getAddress()));
        b.setCity(blank(r.getCity()));
        b.setCountry(blank(r.getCountry()));
        return bookstoreRepository.save(b);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sellableBooks() {
        return bookRepository.findByBookStatusInOrderByTitle(List.of(PublishedBook.PUBLISHED)).stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bookId", b.getBookId());
            m.put("title", b.getTitle());
            m.put("isbn", b.getIsbn());
            m.put("listPrice", b.getPrice());
            m.put("tradePrice", b.getPrice().multiply(TRADE_PRICE_FACTOR).setScale(2, RoundingMode.HALF_UP));
            m.put("available", inventoryService.available(b.getBookId()));
            return m;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> bookstoreOrders(String status) {
        List<BookstoreOrder> orders = status == null || status.isBlank()
                ? bookstoreOrderRepository.findAllByOrderByOrderDateDesc()
                : bookstoreOrderRepository.findByOrderStatusOrderByOrderDateAsc(status.toUpperCase());
        return orders.stream().map(this::bookstoreOrderView).toList();
    }

    public Map<String, Object> createBookstoreOrder(BookstoreOrderRequest r, Long userId) {
        Bookstore store = bookstoreRepository.findById(r.getBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Bookstore not found: " + r.getBookstoreId()));
        if (!"ACTIVE".equals(store.getStatus())) {
            throw new BusinessRuleException(store.getBookstoreName() + " is not an active bookstore");
        }
        BookstoreOrder o = new BookstoreOrder();
        o.setBookstoreId(store.getBookstoreId());
        o.setOrderNumber(numbers.next("BO-", "BOOKSTORE_ORDER", LocalDate.now().getYear()));
        o.setOrderStatus(BookstoreOrder.PENDING_APPROVAL);
        o.setCreatedBy(userId);
        o.setTotalAmount(BigDecimal.ZERO);
        BookstoreOrder saved = bookstoreOrderRepository.saveAndFlush(o);
        BigDecimal total = BigDecimal.ZERO;
        for (BookstoreOrderRequest.Line l : r.getItems()) {
            PublishedBook b = bookRepository.findById(l.getBookId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + l.getBookId()));
            if (!PublishedBook.PUBLISHED.equals(b.getBookStatus())) {
                throw new BusinessRuleException("\"" + b.getTitle() + "\" is not published yet");
            }
            BigDecimal unit = l.getUnitPrice() != null ? l.getUnitPrice()
                    : b.getPrice().multiply(TRADE_PRICE_FACTOR).setScale(2, RoundingMode.HALF_UP);
            BookstoreOrderItem item = new BookstoreOrderItem();
            item.setBookstoreOrderId(saved.getBookstoreOrderId());
            item.setBookId(b.getBookId());
            item.setQuantity(l.getQuantity());
            item.setUnitPrice(unit);
            item.setSubtotal(unit.multiply(BigDecimal.valueOf(l.getQuantity())));
            bookstoreItemRepository.save(item);
            total = total.add(item.getSubtotal());
        }
        saved.setTotalAmount(total);
        return bookstoreOrderView(bookstoreOrderRepository.save(saved));
    }

    /** US30: approval reserves the stock; an order that cannot be covered is refused. */
    public Map<String, Object> approve(Long orderId, Long userId) {
        BookstoreOrder o = bookstoreOrder(orderId);
        requireStatus(o.getOrderStatus(), BookstoreOrder.PENDING_APPROVAL, "approve", o.getOrderNumber());
        Map<Long, String> titles = titles();
        for (BookstoreOrderItem i : bookstoreItemRepository.findByBookstoreOrderId(orderId)) {
            inventoryService.reserve(i.getBookId(), i.getQuantity(), "BOOKSTORE_ORDER", orderId, userId,
                    titles.getOrDefault(i.getBookId(), "Book #" + i.getBookId()));
        }
        o.setOrderStatus(BookstoreOrder.APPROVED);
        o.setApprovedBy(userId);
        o.setApprovedAt(LocalDateTime.now());
        BookstoreOrder saved = bookstoreOrderRepository.save(o);
        auditService.record(userId, "WHOLESALE_APPROVED", "BookstoreOrder", orderId, o.getOrderNumber());
        return bookstoreOrderView(saved);
    }

    public Map<String, Object> reject(Long orderId, String reason, Long userId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("A reason is required to reject a wholesale order");
        }
        BookstoreOrder o = bookstoreOrder(orderId);
        requireStatus(o.getOrderStatus(), BookstoreOrder.PENDING_APPROVAL, "reject", o.getOrderNumber());
        o.setOrderStatus(BookstoreOrder.REJECTED);
        o.setRejectionReason(reason.trim());
        o.setApprovedBy(userId);
        o.setApprovedAt(LocalDateTime.now());
        BookstoreOrder saved = bookstoreOrderRepository.save(o);
        auditService.record(userId, "WHOLESALE_REJECTED", "BookstoreOrder", orderId, reason.trim());
        return bookstoreOrderView(saved);
    }

    public Map<String, Object> dispatchBookstoreOrder(Long orderId, String provider, String tracking, Long userId) {
        BookstoreOrder o = bookstoreOrder(orderId);
        requireStatus(o.getOrderStatus(), BookstoreOrder.APPROVED, "dispatch", o.getOrderNumber());
        if (provider == null || provider.isBlank()) {
            throw new InvalidRequestException("Enter the courier / shipping provider");
        }
        for (BookstoreOrderItem i : bookstoreItemRepository.findByBookstoreOrderId(orderId)) {
            inventoryService.shipReserved(i.getBookId(), i.getQuantity(), "BOOKSTORE_ORDER", orderId, userId);
        }
        o.setShippingProvider(provider.trim());
        o.setTrackingNumber(blank(tracking));
        o.setOrderStatus(BookstoreOrder.DISPATCHED);
        o.setDispatchedAt(LocalDateTime.now());
        return bookstoreOrderView(bookstoreOrderRepository.save(o));
    }

    public Map<String, Object> deliverBookstoreOrder(Long orderId, Long userId) {
        BookstoreOrder o = bookstoreOrder(orderId);
        requireStatus(o.getOrderStatus(), BookstoreOrder.DISPATCHED, "mark delivered", o.getOrderNumber());
        o.setOrderStatus(BookstoreOrder.DELIVERED);
        o.setDeliveredAt(LocalDateTime.now());
        BookstoreOrder saved = bookstoreOrderRepository.save(o);
        Map<Long, String> titles = titles();
        int line = 1;
        for (BookstoreOrderItem i : bookstoreItemRepository.findByBookstoreOrderId(orderId)) {
            salesFeed.completedLine(SalesFeed.WHOLESALE, o.getOrderNumber(), line++, i.getBookId(),
                    titles.getOrDefault(i.getBookId(), "Book #" + i.getBookId()), i.getQuantity(), i.getUnitPrice(), BigDecimal.ZERO);
        }
        return bookstoreOrderView(saved);
    }

    private Map<String, Object> bookstoreOrderView(BookstoreOrder o) {
        Map<Long, String> titles = titles();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", o);
        m.put("bookstoreName", bookstoreRepository.findById(o.getBookstoreId()).map(Bookstore::getBookstoreName).orElse(null));
        m.put("createdByName", names.user(o.getCreatedBy()));
        m.put("approvedByName", names.user(o.getApprovedBy()));
        m.put("items", bookstoreItemRepository.findByBookstoreOrderId(o.getBookstoreOrderId()).stream().map(i -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bookId", i.getBookId());
            row.put("title", titles.getOrDefault(i.getBookId(), "Book #" + i.getBookId()));
            row.put("quantity", i.getQuantity());
            row.put("unitPrice", i.getUnitPrice());
            row.put("subtotal", i.getSubtotal());
            row.put("available", inventoryService.available(i.getBookId()));
            return row;
        }).toList());
        return m;
    }

    private BookstoreOrder bookstoreOrder(Long id) {
        return bookstoreOrderRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Wholesale order not found: " + id));
    }

    private Map<Long, String> titles() {
        return bookRepository.findAll().stream().collect(Collectors.toMap(PublishedBook::getBookId, PublishedBook::getTitle));
    }

    private static void requireStatus(String actual, String required, String action, String number) {
        if (!required.equals(actual)) {
            throw new BusinessRuleException("Cannot " + action + " order " + number + ": it is " + actual.replace('_', ' ')
                    + " (must be " + required.replace('_', ' ') + ")");
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
