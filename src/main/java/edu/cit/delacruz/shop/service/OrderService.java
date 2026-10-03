package edu.cit.delacruz.shop.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;
import edu.cit.delacruz.inventory.service.ReservationLine;
import edu.cit.delacruz.shop.event.OrderBackorderedEvent;
import edu.cit.delacruz.shop.event.OrderCancelledEvent;
import edu.cit.delacruz.shop.event.OrderPlacedEvent;
import edu.cit.delacruz.shop.event.OrderRejectedEvent;
import edu.cit.delacruz.shop.model.Order;
import edu.cit.delacruz.shop.model.OrderItem;
import edu.cit.delacruz.shop.repository.OrderRepository;

@Service
public class OrderService {

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_BACKORDERED = "BACKORDERED";

    private static final String OUTCOME_OK = "OK";
    private static final String OUTCOME_RESERVED = "RESERVED";
    private static final String OUTCOME_NOT_ATTEMPTED = "NOT_ATTEMPTED";
    private static final String OUTCOME_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    private static final String OUTCOME_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    private static final String OUTCOME_INVALID_QUANTITY = "INVALID_QUANTITY";
    private static final String OUTCOME_BACKORDERED = "BACKORDERED";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places a (possibly multi-item) order.
     * <p>
     * Pass 1 validates every line against current stock. If ANY line
     * fails, the entire order is rejected and NOTHING is reserved — the
     * lines that would have individually succeeded are marked
     * NOT_ATTEMPTED rather than partially fulfilled.
     * <p>
     * Only if every line passes does pass 2 call
     * {@link InventoryService#reserveAll}, which reserves them all inside
     * one transaction. This whole method is itself {@code @Transactional},
     * so if a genuine race condition causes reserveAll() to fail anyway
     * (stock changed between pass 1's check and pass 2's reservation),
     * the exception propagates uncaught and the entire transaction —
     * every reservation just made, and this method's Order save — rolls
     * back together. We deliberately don't catch it and write a "soft"
     * REJECTED order in that case: doing so on the same transaction would
     * hit Spring's rollback-only guard (the transaction is already marked
     * for rollback once a RuntimeException escapes reserveAll), and
     * doing it on a fresh transaction would mean recording an order for
     * a request whose ordinary validation said it should succeed. This
     * is an accepted limitation for a single-writer lab environment, not
     * something the required test scenarios exercise.
     */
    @Transactional
    public Map<String, Object> placeOrder(List<OrderLine> lines) {

        if (lines == null || lines.isEmpty()) {
            return persistOrder(List.of(), List.of(), STATUS_REJECTED, "Order must contain at least one item.");
        }

        List<String> lineOutcomes = new ArrayList<>();
        String rejectionReason = null;

        for (OrderLine line : lines) {
            InventoryItem item = inventoryService.getItem(line.productId());

            if (item == null) {
                lineOutcomes.add(OUTCOME_PRODUCT_NOT_FOUND);
                rejectionReason = "Product not found: " + line.productId() + ".";
            } else if (line.quantity() <= 0) {
                lineOutcomes.add(OUTCOME_INVALID_QUANTITY);
                rejectionReason = "Quantity must be greater than zero for " + line.productId() + ".";
            } else if (line.quantity() > item.getStock()) {
                lineOutcomes.add(OUTCOME_INSUFFICIENT_STOCK);
                rejectionReason = "Insufficient stock for " + line.productId() + ": requested "
                        + line.quantity() + " but only " + item.getStock() + " available.";
            } else {
                lineOutcomes.add(OUTCOME_OK);
            }
        }

        boolean anyLineInvalid = lineOutcomes.stream().anyMatch(outcome -> !OUTCOME_OK.equals(outcome));

        if (anyLineInvalid) {
            List<String> finalOutcomes = lineOutcomes.stream()
                    .map(outcome -> OUTCOME_OK.equals(outcome) ? OUTCOME_NOT_ATTEMPTED : outcome)
                    .toList();
            return persistOrder(lines, finalOutcomes, STATUS_REJECTED, rejectionReason);
        }

        List<ReservationLine> reservationLines = lines.stream()
                .map(line -> new ReservationLine(line.productId(), line.quantity()))
                .toList();
        inventoryService.reserveAll(reservationLines);

        List<String> reservedOutcomes = lines.stream().map(line -> OUTCOME_RESERVED).toList();

        return persistOrder(lines, reservedOutcomes, STATUS_CONFIRMED, "Order confirmed.");
    }

    @Transactional
    public Map<String, Object> cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (STATUS_CANCELLED.equals(order.getStatus())) {
            throw new OrderCancellationException("Order " + orderId + " is already cancelled.");
        }

        if (!STATUS_CONFIRMED.equals(order.getStatus())) {
            throw new OrderCancellationException(
                    "Only confirmed orders can be cancelled (order " + orderId + " is "
                            + order.getStatus() + ").");
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.restock(item.getProductId(), item.getQuantity());
            item.setOutcome(STATUS_CANCELLED);
        }

        order.setStatus(STATUS_CANCELLED);
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCancelledEvent(order.getOrderId()));

        List<InventoryItem> inventorySnapshot = order.getItems().stream()
                .map(item -> inventoryService.getItem(item.getProductId()))
                .filter(item -> item != null)
                .toList();

        return buildResponse(order, "Order cancelled; reserved quantities returned to stock.", inventorySnapshot);
    }

    /**
     * Creates an order that reserves nothing yet - for a caller (the
     * channel module, for Tiangge's BACKORDERED decision) that already
     * knows the supplier has stock coming but doesn't have it on hand now.
     * No stock-validation pass runs here; the caller decided this is a
     * backorder already knowing current stock is insufficient.
     */
    @Transactional
    public Map<String, Object> placeBackorder(List<OrderLine> lines) {
        List<String> outcomes = lines.stream().map(line -> OUTCOME_BACKORDERED).toList();
        return persistOrder(lines, outcomes, STATUS_BACKORDERED, "Backordered; stock is on the way from the supplier.");
    }

    /**
     * Reserves a backordered order's stock now that it's believed to be
     * available, and confirms it. Lets InsufficientStockException escape
     * uncaught if stock turns out not to be there after all (e.g. another
     * backorder claimed it first) - the caller decides what to do next
     * (typically {@link #cancelBackorder}), the same way placeOrder leaves
     * a genuine race to its own caller rather than guessing on its behalf.
     *
     * @throws IllegalStateException if the order is not currently BACKORDERED
     */
    @Transactional
    public Map<String, Object> fulfillBackorder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!STATUS_BACKORDERED.equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Order " + orderId + " is not backordered (status " + order.getStatus() + ").");
        }

        List<ReservationLine> reservationLines = order.getItems().stream()
                .map(item -> new ReservationLine(item.getProductId(), item.getQuantity()))
                .toList();
        inventoryService.reserveAll(reservationLines);

        for (OrderItem item : order.getItems()) {
            item.setOutcome(OUTCOME_RESERVED);
        }
        order.setStatus(STATUS_CONFIRMED);
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));

        List<InventoryItem> inventorySnapshot = order.getItems().stream()
                .map(item -> inventoryService.getItem(item.getProductId()))
                .filter(item -> item != null)
                .toList();

        return buildResponse(order, "Backorder fulfilled; stock reserved.", inventorySnapshot);
    }

    /**
     * Gives up on a backordered order. Unlike {@link #cancelOrder}, this
     * never calls {@code restock()} - nothing was ever reserved for a
     * backorder, so there is nothing to give back.
     *
     * @throws IllegalStateException if the order is not currently BACKORDERED
     */
    @Transactional
    public Map<String, Object> cancelBackorder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!STATUS_BACKORDERED.equals(order.getStatus())) {
            throw new IllegalStateException(
                    "Order " + orderId + " is not backordered (status " + order.getStatus() + ").");
        }

        for (OrderItem item : order.getItems()) {
            item.setOutcome(STATUS_CANCELLED);
        }
        order.setStatus(STATUS_CANCELLED);
        orderRepository.save(order);

        // Reusing OrderRejectedEvent rather than OrderCancelledEvent: the
        // latter's documented contract is "stock restored", which isn't
        // true here. "This order did not go through, here's why" is
        // exactly what OrderRejectedEvent already means.
        eventPublisher.publishEvent(
                new OrderRejectedEvent(order.getOrderId(), "Backorder could not be filled by the supplier."));

        return buildResponse(order, "Backorder cancelled; supplier could not fill it.", List.of());
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderId"));
    }

    private Map<String, Object> persistOrder(
            List<OrderLine> lines,
            List<String> outcomes,
            String status,
            String reason
    ) {
        Order order = new Order(status, reason, LocalDateTime.now());
        for (int i = 0; i < lines.size(); i++) {
            order.addItem(new OrderItem(lines.get(i).productId(), lines.get(i).quantity(), outcomes.get(i)));
        }
        orderRepository.save(order);

        if (STATUS_CONFIRMED.equals(status)) {
            eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));
        } else if (STATUS_BACKORDERED.equals(status)) {
            eventPublisher.publishEvent(new OrderBackorderedEvent(order.getOrderId()));
        } else {
            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));
        }

        Set<String> seenProductIds = new LinkedHashSet<>();
        List<InventoryItem> inventorySnapshot = new ArrayList<>();
        for (OrderLine line : lines) {
            if (seenProductIds.add(line.productId())) {
                InventoryItem current = inventoryService.getItem(line.productId());
                if (current != null) {
                    inventorySnapshot.add(current);
                }
            }
        }

        return buildResponse(order, order.getReason(), inventorySnapshot);
    }

    private Map<String, Object> buildResponse(Order order, String reason, List<InventoryItem> inventorySnapshot) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("productId", item.getProductId());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("outcome", item.getOutcome());
            items.add(itemMap);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("status", order.getStatus());
        response.put("reason", reason);
        response.put("items", items);
        response.put("inventory", inventorySnapshot);
        return response;
    }
}
