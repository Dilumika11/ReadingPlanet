package com.epms.service;

import com.epms.dto.request.CheckoutRequest;
import com.epms.dto.response.CatalogResponse;
import com.epms.entity.Book;
import com.epms.entity.CartItem;
import com.epms.entity.CustomerOrder;
import com.epms.entity.CustomerOrderItem;
import com.epms.entity.PublishedBook;
import com.epms.entity.Shipment;
import com.epms.exception.BusinessRuleException;
import com.epms.exception.InvalidRequestException;
import com.epms.exception.ResourceNotFoundException;
import com.epms.repository.BookRepository;
import com.epms.repository.CartItemRepository;
import com.epms.repository.CustomerOrderItemRepository;
import com.epms.repository.CustomerOrderRepository;
import com.epms.repository.PublishedBookRepository;
import com.epms.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Epic 3 (US25 - US27, US29): browsing, cart, ordering and tracking for customers. */
@Service
@RequiredArgsConstructor
@Transactional
public class StoreService {

    public static final int MAX_PER_TITLE = 50;

    private final BookService bookService;
    private final BookRepository catalogRepository;
    private final CartItemRepository cartRepository;
    private final CustomerOrderRepository orderRepository;
    private final CustomerOrderItemRepository itemRepository;
    private final ShipmentRepository shipmentRepository;
    private final PublishedBookRepository publishedBookRepository;
    private final InventoryService inventoryService;
    private final DocumentNumberService numbers;

    // ---------- US25: browse, search, filter ----------

    @Transactional(readOnly = true)
    public List<CatalogResponse.CatalogBook> search(String q, Long categoryId, Long genreId, BigDecimal minPrice,
                                                    BigDecimal maxPrice, boolean inStockOnly, String sort) {
        if (minPrice != null && maxPrice != null && maxPrice.compareTo(minPrice) < 0) {
            throw new InvalidRequestException("The maximum price cannot be lower than the minimum price");
        }
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Comparator<CatalogResponse.CatalogBook> order = switch (sort == null ? "" : sort) {
            case "price_asc" -> Comparator.comparing(CatalogResponse.CatalogBook::getPrice);
            case "price_desc" -> Comparator.comparing(CatalogResponse.CatalogBook::getPrice).reversed();
            case "title" -> Comparator.comparing(b -> b.getTitle().toLowerCase(Locale.ROOT));
            default -> (a, b) -> 0; // newest first, as the catalogue is already ordered
        };
        return bookService.getCatalog().getBooks().stream()
                .filter(b -> needle.isEmpty() || contains(b.getTitle(), needle) || contains(b.getAuthor(), needle)
                        || contains(b.getIsbn(), needle))
                .filter(b -> categoryId == null || categoryId.equals(b.getCategoryId()))
                .filter(b -> genreId == null || genreId.equals(b.getGenreId()))
                .filter(b -> minPrice == null || b.getPrice().compareTo(minPrice) >= 0)
                .filter(b -> maxPrice == null || b.getPrice().compareTo(maxPrice) <= 0)
                .filter(b -> !inStockOnly || b.isOrderable())
                .sorted(order)
                .collect(Collectors.toList());
    }

    private static boolean contains(String s, String needle) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
    }

    // ---------- US26: cart ----------

    @Transactional(readOnly = true)
    public Map<String, Object> cart(Long userId) {
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        for (CartItem c : cartRepository.findByUserIdOrderByAddedAt(userId)) {
            Book b = catalogRepository.findById(c.getCatalogBookId()).orElse(null);
            if (b == null) continue;
            int available = b.getStockBookId() == null ? 0 : Math.max(0, inventoryService.available(b.getStockBookId()));
            BigDecimal line = b.getPrice().multiply(BigDecimal.valueOf(c.getQuantity()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cartItemId", c.getCartItemId());
            m.put("catalogBookId", b.getBookId());
            m.put("title", b.getTitle());
            m.put("author", b.getAuthorName());
            m.put("price", b.getPrice());
            m.put("quantity", c.getQuantity());
            m.put("lineTotal", line);
            m.put("available", available);
            m.put("enoughStock", available >= c.getQuantity());
            m.put("coverUrl", bookService.coverUrl(b.getCoverImage()));
            items.add(m);
            subtotal = subtotal.add(line);
            count += c.getQuantity();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("itemCount", count);
        out.put("subtotal", subtotal);
        return out;
    }

    public Map<String, Object> addToCart(Long userId, Long catalogBookId, int quantity) {
        Book b = orderableListing(catalogBookId);
        CartItem c = cartRepository.findByUserIdAndCatalogBookId(userId, catalogBookId).orElseGet(() -> {
            CartItem n = new CartItem();
            n.setUserId(userId);
            n.setCatalogBookId(catalogBookId);
            n.setQuantity(0);
            return n;
        });
        setQuantity(c, b, c.getQuantity() + quantity);
        return cart(userId);
    }

    public Map<String, Object> updateCartItem(Long userId, Long cartItemId, int quantity) {
        CartItem c = ownItem(userId, cartItemId);
        if (quantity <= 0) {
            cartRepository.delete(c);
        } else {
            setQuantity(c, orderableListing(c.getCatalogBookId()), quantity);
        }
        return cart(userId);
    }

    public Map<String, Object> removeCartItem(Long userId, Long cartItemId) {
        cartRepository.delete(ownItem(userId, cartItemId));
        return cart(userId);
    }

    private void setQuantity(CartItem c, Book b, int quantity) {
        if (quantity < 1) {
            throw new InvalidRequestException("Quantity must be at least 1");
        }
        if (quantity > MAX_PER_TITLE) {
            throw new InvalidRequestException("You can order up to " + MAX_PER_TITLE + " copies of a title online");
        }
        int available = inventoryService.available(b.getStockBookId());
        if (quantity > available) {
            throw new BusinessRuleException("Only " + Math.max(0, available) + " copies of \"" + b.getTitle() + "\" are in stock");
        }
        c.setQuantity(quantity);
        cartRepository.save(c);
    }

    private Book orderableListing(Long catalogBookId) {
        Book b = catalogRepository.findById(catalogBookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found: " + catalogBookId));
        if (b.getStockBookId() == null) {
            throw new BusinessRuleException("\"" + b.getTitle() + "\" is not available to order yet");
        }
        return b;
    }

    private CartItem ownItem(Long userId, Long cartItemId) {
        CartItem c = cartRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + cartItemId));
        if (!c.getUserId().equals(userId)) {
            throw new AccessDeniedException("That item is not in your cart");
        }
        return c;
    }

    // ---------- US27: place order and reserve stock ----------

    public Map<String, Object> checkout(Long userId, CheckoutRequest r) {
        List<CartItem> cart = cartRepository.findByUserIdOrderByAddedAt(userId);
        if (cart.isEmpty()) {
            throw new BusinessRuleException("Your cart is empty");
        }
        CustomerOrder order = new CustomerOrder();
        order.setCustomerId(userId);
        order.setOrderNumber(numbers.next("CO-", "CUSTOMER_ORDER", LocalDate.now().getYear()));
        order.setRecipientName(r.getRecipientName().trim());
        order.setRecipientPhone(r.getRecipientPhone().trim());
        order.setShippingAddress(r.getShippingAddress().trim());
        order.setOrderStatus(CustomerOrder.CONFIRMED);
        order.setPaymentStatus("PENDING");
        order.setTotalAmount(BigDecimal.ZERO);
        CustomerOrder saved = orderRepository.saveAndFlush(order);

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem c : cart) {
            Book b = orderableListing(c.getCatalogBookId());
            // Throws (and rolls the whole order back) if another customer took the last copies.
            inventoryService.reserve(b.getStockBookId(), c.getQuantity(), "CUSTOMER_ORDER", saved.getCustomerOrderId(),
                    userId, b.getTitle());
            CustomerOrderItem item = new CustomerOrderItem();
            item.setCustomerOrderId(saved.getCustomerOrderId());
            item.setBookId(b.getStockBookId());
            item.setQuantity(c.getQuantity());
            item.setUnitPrice(b.getPrice());
            item.setSubtotal(b.getPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
            itemRepository.save(item);
            total = total.add(item.getSubtotal());
        }
        saved.setTotalAmount(total);
        orderRepository.save(saved);
        cartRepository.deleteByUserId(userId);
        return orderView(saved);
    }

    // ---------- US29: my orders and tracking ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myOrders(Long userId) {
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(userId).stream().map(this::orderView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myOrder(Long userId, Long orderId) {
        return orderView(own(userId, orderId));
    }

    /** Before dispatch the customer can cancel; the reserved stock is released. */
    public Map<String, Object> cancel(Long userId, Long orderId, String reason) {
        CustomerOrder o = own(userId, orderId);
        if (!CustomerOrder.CONFIRMED.equals(o.getOrderStatus()) && !CustomerOrder.PACKED.equals(o.getOrderStatus())) {
            throw new BusinessRuleException("Order " + o.getOrderNumber() + " has already been dispatched and cannot be cancelled");
        }
        for (CustomerOrderItem i : itemRepository.findByCustomerOrderId(orderId)) {
            inventoryService.release(i.getBookId(), i.getQuantity(), "CUSTOMER_ORDER", orderId, userId);
        }
        o.setOrderStatus(CustomerOrder.CANCELLED);
        o.setCancelReason(reason == null || reason.isBlank() ? "Cancelled by the customer" : reason.trim());
        return orderView(orderRepository.save(o));
    }

    private CustomerOrder own(Long userId, Long orderId) {
        CustomerOrder o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!o.getCustomerId().equals(userId)) {
            throw new AccessDeniedException("You can only view your own orders");
        }
        return o;
    }

    /** Order with items, shipment and a tracking timeline. */
    public Map<String, Object> orderView(CustomerOrder o) {
        Map<Long, String> titles = publishedBookRepository.findAll().stream()
                .collect(Collectors.toMap(PublishedBook::getBookId, PublishedBook::getTitle));
        List<Map<String, Object>> items = itemRepository.findByCustomerOrderId(o.getCustomerOrderId()).stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bookId", i.getBookId());
            m.put("title", titles.getOrDefault(i.getBookId(), "Book #" + i.getBookId()));
            m.put("quantity", i.getQuantity());
            m.put("unitPrice", i.getUnitPrice());
            m.put("subtotal", i.getSubtotal());
            return m;
        }).toList();
        Shipment s = shipmentRepository.findByCustomerOrderId(o.getCustomerOrderId()).orElse(null);
        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(step("Order placed", o.getOrderDate(), true));
        if (CustomerOrder.CANCELLED.equals(o.getOrderStatus())) {
            timeline.add(step("Cancelled: " + o.getCancelReason(), o.getUpdatedAt(), true));
        } else {
            timeline.add(step("Packed", o.getPackedAt(), o.getPackedAt() != null));
            timeline.add(step(s == null ? "Dispatched" : "Dispatched with " + s.getShippingProvider()
                    + (s.getTrackingNumber() == null ? "" : ", tracking " + s.getTrackingNumber()), o.getDispatchedAt(),
                    o.getDispatchedAt() != null));
            timeline.add(step("Delivered", o.getDeliveredAt(), o.getDeliveredAt() != null));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", o);
        m.put("items", items);
        m.put("shipment", s);
        m.put("timeline", timeline);
        return m;
    }

    private static Map<String, Object> step(String label, Object at, boolean done) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("at", at);
        m.put("done", done);
        return m;
    }
}
