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
import edu.cit.delacruz.shop.event.OrderCancelledEvent;
import edu.cit.delacruz.shop.event.OrderPlacedEvent;
import edu.cit.delacruz.shop.event.OrderRejectedEvent;
import edu.cit.delacruz.shop.model.Order;
import edu.cit.delacruz.shop.model.OrderItem;
import edu.cit.delacruz.shop.repository.OrderRepository;

@Service
public class OrderService {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String OUTCOME_OK = "OK";
    private static final String OUTCOME_RESERVED = "RESERVED";
    private static final String OUTCOME_NOT_ATTEMPTED = "NOT_ATTEMPTED";
    private static final String OUTCOME_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    private static final String OUTCOME_PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
    private static final String OUTCOME_INVALID_QUANTITY = "INVALID_QUANTITY";

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
