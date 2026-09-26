package com.epms.controller;

import com.epms.dto.request.BookstoreOrderRequest;
import com.epms.dto.request.BookstoreRequest;
import com.epms.dto.request.CheckoutRequest;
import com.epms.dto.request.ReasonRequest;
import com.epms.dto.response.ApiResponse;
import com.epms.security.service.CurrentUserService;
import com.epms.service.FulfilmentService;
import com.epms.service.InventoryService;
import com.epms.service.PrintOrderService;
import com.epms.service.StoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Epic 3 (US21 - US30). Role rules per path prefix are in SecurityConfig:
 * /api/production (production manager), /api/warehouse (inventory staff),
 * /api/customer (customers), /api/sales (sales staff), /api/public (anyone).
 */
@RestController
@RequiredArgsConstructor
public class OperationsController {

    private final PrintOrderService printOrders;
    private final InventoryService inventory;
    private final StoreService store;
    private final FulfilmentService fulfilment;
    private final CurrentUserService currentUserService;

    // ===================== print jobs (US21, US22) =====================

    @Data
    public static class PrintOrderRequest {
        private Long bookId;
        private Integer quantity;
        private String printingCompany;
        private LocalDate expectedCompletionDate;
        private String status;
    }

    @GetMapping("/api/production/books/approved")
    public ApiResponse<?> approvedBooks() {
        return new ApiResponse<>(true, "Books ready for printing", printOrders.approvedBooks());
    }

    @GetMapping("/api/production/print-orders")
    public ApiResponse<?> printOrders() {
        return new ApiResponse<>(true, "Print jobs", printOrders.list());
    }

    @GetMapping("/api/production/print-orders/{id}")
    public ApiResponse<?> printOrder(@PathVariable Long id) {
        return new ApiResponse<>(true, "Print job", printOrders.get(id));
    }

    @PostMapping("/api/production/print-orders")
    public ApiResponse<?> createPrintOrder(@RequestBody PrintOrderRequest r, Authentication auth) {
        if (r.getBookId() == null) {
            throw new com.epms.exception.InvalidRequestException("Choose the book to print");
        }
        return new ApiResponse<>(true, "Print job created", printOrders.create(r.getBookId(), r.getQuantity(),
                r.getPrintingCompany(), r.getExpectedCompletionDate(), uid(auth)));
    }

    @PutMapping("/api/production/print-orders/{id}")
    public ApiResponse<?> updatePrintOrder(@PathVariable Long id, @RequestBody PrintOrderRequest r) {
        return new ApiResponse<>(true, "Print job updated", printOrders.update(id, r.getQuantity(), r.getPrintingCompany(),
                r.getExpectedCompletionDate(), r.getStatus()));
    }

    @DeleteMapping("/api/production/print-orders/{id}")
    public ApiResponse<?> deletePrintOrder(@PathVariable Long id) {
        printOrders.delete(id);
        return new ApiResponse<>(true, "Print job deleted", null);
    }

    // ===================== warehouse stock (US23, US24) =====================

    @Data
    public static class ReceiveRequest {
        @NotNull(message = "Choose the print order")
        private Long printOrderId;
        @NotNull(message = "Enter the quantity received")
        @Min(value = 1, message = "Quantity received must be at least 1")
        private Integer quantityReceived;
        private String warehouseLocation;
        private String remarks;
    }

    @Data
    public static class AdjustRequest {
        @NotNull(message = "Choose the book")
        private Long bookId;
        @NotNull(message = "Choose the adjustment type")
        private String adjustmentType;
        private String direction;
        @NotNull(message = "Enter the quantity")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
        private String remarks;
    }

    @GetMapping("/api/warehouse/inventory")
    public ApiResponse<?> inventory() {
        return new ApiResponse<>(true, "Inventory", inventory.inventory());
    }

    @GetMapping("/api/warehouse/transactions")
    public ApiResponse<?> transactions() {
        return new ApiResponse<>(true, "Stock movements", inventory.transactions());
    }

    @GetMapping("/api/warehouse/print-orders/completed")
    public ApiResponse<?> completedPrintOrders() {
        return new ApiResponse<>(true, "Completed print jobs to receive", inventory.completedPrintOrders());
    }

    @PostMapping("/api/warehouse/inventory/receive")
    public ApiResponse<?> receive(@Valid @RequestBody ReceiveRequest r, Authentication auth) {
        inventory.receive(r.getPrintOrderId(), r.getQuantityReceived(), r.getWarehouseLocation(), r.getRemarks(), uid(auth));
        return new ApiResponse<>(true, r.getQuantityReceived() + " copies received into stock", null);
    }

    @PostMapping("/api/warehouse/inventory/adjust")
    public ApiResponse<?> adjust(@Valid @RequestBody AdjustRequest r, Authentication auth) {
        inventory.adjust(r.getBookId(), r.getAdjustmentType(), r.getDirection(), r.getQuantity(), r.getRemarks(), uid(auth));
        return new ApiResponse<>(true, "Stock adjustment recorded", null);
    }

    // ===================== warehouse fulfilment (US28) =====================

    @Data
    public static class DispatchRequest {
        private String shippingProvider;
        private String trackingNumber;
        private BigDecimal shippingCost;
    }

    @GetMapping("/api/warehouse/orders")
    public ApiResponse<?> customerOrders(@RequestParam(required = false) String status) {
        return new ApiResponse<>(true, "Customer orders", fulfilment.customerOrders(status));
    }

    @PostMapping("/api/warehouse/orders/{id}/pack")
    public ApiResponse<?> pack(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Order packed", fulfilment.pack(id, uid(auth)));
    }

    @PostMapping("/api/warehouse/orders/{id}/dispatch")
    public ApiResponse<?> dispatch(@PathVariable Long id, @RequestBody DispatchRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Order dispatched",
                fulfilment.dispatch(id, r.getShippingProvider(), r.getTrackingNumber(), r.getShippingCost(), uid(auth)));
    }

    @PostMapping("/api/warehouse/orders/{id}/deliver")
    public ApiResponse<?> deliver(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Order delivered", fulfilment.deliver(id, uid(auth)));
    }

    @GetMapping("/api/warehouse/bookstore-orders")
    public ApiResponse<?> wholesaleToFulfil(@RequestParam(required = false) String status) {
        return new ApiResponse<>(true, "Wholesale orders", fulfilment.bookstoreOrders(status));
    }

    @PostMapping("/api/warehouse/bookstore-orders/{id}/dispatch")
    public ApiResponse<?> dispatchWholesale(@PathVariable Long id, @RequestBody DispatchRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Wholesale order dispatched",
                fulfilment.dispatchBookstoreOrder(id, r.getShippingProvider(), r.getTrackingNumber(), uid(auth)));
    }

    @PostMapping("/api/warehouse/bookstore-orders/{id}/deliver")
    public ApiResponse<?> deliverWholesale(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Wholesale order delivered", fulfilment.deliverBookstoreOrder(id, uid(auth)));
    }

    // ===================== public store search (US25) =====================

    @GetMapping("/api/public/books")
    public ApiResponse<?> search(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Long categoryId,
                                 @RequestParam(required = false) Long genreId,
                                 @RequestParam(required = false) BigDecimal minPrice,
                                 @RequestParam(required = false) BigDecimal maxPrice,
                                 @RequestParam(required = false, defaultValue = "false") boolean inStock,
                                 @RequestParam(required = false) String sort) {
        return new ApiResponse<>(true, "Books", store.search(q, categoryId, genreId, minPrice, maxPrice, inStock, sort));
    }

    // ===================== customer cart, orders, tracking (US26, US27, US29) =====================

    @Data
    public static class CartRequest {
        private Long catalogBookId;
        @NotNull(message = "Enter a quantity")
        private Integer quantity;
    }

    @GetMapping("/api/customer/cart")
    public ApiResponse<?> cart(Authentication auth) {
        return new ApiResponse<>(true, "Your cart", store.cart(uid(auth)));
    }

    @PostMapping("/api/customer/cart")
    public ApiResponse<?> addToCart(@Valid @RequestBody CartRequest r, Authentication auth) {
        if (r.getCatalogBookId() == null) {
            throw new com.epms.exception.InvalidRequestException("Choose a book");
        }
        return new ApiResponse<>(true, "Added to cart", store.addToCart(uid(auth), r.getCatalogBookId(), r.getQuantity()));
    }

    @PutMapping("/api/customer/cart/{itemId}")
    public ApiResponse<?> updateCart(@PathVariable Long itemId, @Valid @RequestBody CartRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Cart updated", store.updateCartItem(uid(auth), itemId, r.getQuantity()));
    }

    @DeleteMapping("/api/customer/cart/{itemId}")
    public ApiResponse<?> removeFromCart(@PathVariable Long itemId, Authentication auth) {
        return new ApiResponse<>(true, "Removed from cart", store.removeCartItem(uid(auth), itemId));
    }

    @PostMapping("/api/customer/orders")
    public ApiResponse<?> checkout(@Valid @RequestBody CheckoutRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Order placed", store.checkout(uid(auth), r));
    }

    @GetMapping("/api/customer/orders")
    public ApiResponse<?> myOrders(Authentication auth) {
        return new ApiResponse<>(true, "Your orders", store.myOrders(uid(auth)));
    }

    @GetMapping("/api/customer/orders/{id}")
    public ApiResponse<?> myOrder(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Order", store.myOrder(uid(auth), id));
    }

    @PostMapping("/api/customer/orders/{id}/cancel")
    public ApiResponse<?> cancel(@PathVariable Long id, @RequestBody(required = false) ReasonRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Order cancelled", store.cancel(uid(auth), id, r == null ? null : r.getReason()));
    }

    // ===================== sales: bookstores and wholesale approval (US30) =====================

    @GetMapping("/api/sales/bookstores")
    public ApiResponse<?> bookstores() {
        return new ApiResponse<>(true, "Bookstores", fulfilment.bookstores());
    }

    @PostMapping("/api/sales/bookstores")
    public ApiResponse<?> createBookstore(@Valid @RequestBody BookstoreRequest r) {
        return new ApiResponse<>(true, "Bookstore added", fulfilment.saveBookstore(null, r));
    }

    @PutMapping("/api/sales/bookstores/{id}")
    public ApiResponse<?> updateBookstore(@PathVariable Long id, @Valid @RequestBody BookstoreRequest r) {
        return new ApiResponse<>(true, "Bookstore updated", fulfilment.saveBookstore(id, r));
    }

    @GetMapping("/api/sales/books")
    public ApiResponse<?> sellableBooks() {
        return new ApiResponse<>(true, "Published books", fulfilment.sellableBooks());
    }

    @GetMapping("/api/sales/bookstore-orders")
    public ApiResponse<?> bookstoreOrders(@RequestParam(required = false) String status) {
        return new ApiResponse<>(true, "Wholesale orders", fulfilment.bookstoreOrders(status));
    }

    @PostMapping("/api/sales/bookstore-orders")
    public ApiResponse<?> createBookstoreOrder(@Valid @RequestBody BookstoreOrderRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Wholesale order created, waiting for approval",
                fulfilment.createBookstoreOrder(r, uid(auth)));
    }

    @PostMapping("/api/sales/bookstore-orders/{id}/approve")
    public ApiResponse<?> approve(@PathVariable Long id, Authentication auth) {
        return new ApiResponse<>(true, "Wholesale order approved", fulfilment.approve(id, uid(auth)));
    }

    @PostMapping("/api/sales/bookstore-orders/{id}/reject")
    public ApiResponse<?> reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest r, Authentication auth) {
        return new ApiResponse<>(true, "Wholesale order rejected", fulfilment.reject(id, r.getReason(), uid(auth)));
    }

    private Long uid(Authentication auth) {
        return currentUserService.getCurrentUserId(auth);
    }
}
