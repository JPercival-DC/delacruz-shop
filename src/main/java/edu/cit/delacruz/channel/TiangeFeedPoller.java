package edu.cit.delacruz.channel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.cit.delacruz.channel.TiangeJson.FeedEvent;
import edu.cit.delacruz.channel.TiangeJson.FeedResponse;
import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;
import edu.cit.delacruz.shop.service.OrderLine;
import edu.cit.delacruz.shop.service.OrderService;
import edu.cit.delacruz.supplier.ReorderResult;
import edu.cit.delacruz.supplier.SupplierGateway;
import edu.cit.delacruz.supplier.SupplierOrderStatus;

/**
 * Tasks 4 and 5. Reads the feed from a durably-stored cursor (restart-safe
 * by construction - the cursor only ever moves forward, one event at a
 * time, and only once that event is actually handled).
 * <p>
 * Exactly-once per Tiangge order is enforced by {@link ChannelOrder}'s
 * unique constraint on tiangeOrderId, not by a separate eventId ledger:
 * seeing the same Tiangge order again (crash-recovery or a genuine
 * redelivery) means re-sending the SAME decision, which the manual
 * guarantees is safe - so there's nothing a dedicated "have I seen this
 * eventId" table would add here.
 * <p>
 * If one event fails to process, this stops the batch right there rather
 * than skipping it - losing an order silently is worse than one batch
 * running a little behind. The cursor is only ever moved past events that
 * were actually handled, so the next tick resumes exactly at the one that
 * failed.
 */
@Component
class TiangeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TiangeFeedPoller.class);

    private final TiangeClient client;
    private final FeedCursorRepository feedCursorRepository;
    private final ChannelOrderRepository channelOrderRepository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final int feedLimit;

    TiangeFeedPoller(
            TiangeClient client,
            FeedCursorRepository feedCursorRepository,
            ChannelOrderRepository channelOrderRepository,
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            @Value("${app.channel.feed-limit:20}") int feedLimit
    ) {
        this.client = client;
        this.feedCursorRepository = feedCursorRepository;
        this.channelOrderRepository = channelOrderRepository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.feedLimit = feedLimit;
    }

    // initialDelay keeps this from racing StartupRunner's listings/stock publish.
    @Scheduled(fixedDelayString = "${app.channel.feed-poll-interval-ms:5000}",
            initialDelayString = "${app.channel.feed-poll-interval-ms:5000}")
    void poll() {
        FeedCursor cursor = feedCursorRepository.findById(FeedCursor.SINGLETON_ID)
                .orElseGet(() -> feedCursorRepository.save(new FeedCursor(0)));

        FeedResponse response;
        try {
            response = client.pollFeed(cursor.getNextCursor(), feedLimit);
        } catch (RuntimeException e) {
            log.warn("Tiangge feed poll failed, will retry next tick: {}", e.getMessage());
            return;
        }

        for (FeedEvent event : response.events()) {
            try {
                if (event.isOrderPlaced()) {
                    handleOrderPlaced(event);
                } else if (event.isOrderCancelled()) {
                    handleOrderCancelled(event);
                } else {
                    log.warn("Tiangge feed: unrecognized event type '{}' (eventId {}), skipping",
                            event.type(), event.eventId());
                }
            } catch (RuntimeException e) {
                log.error("Tiangge feed: stopping at event {} ({}), will retry next tick: {}",
                        event.eventId(), event.type(), e.getMessage());
                return; // cursor already saved past every event before this one
            }
            cursor.setNextCursor(event.seq());
            feedCursorRepository.save(cursor);
        }
    }

    private void handleOrderPlaced(FeedEvent event) {
        ChannelOrder existing = channelOrderRepository.findByTiangeOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            resendDecision(existing); // redelivery or crash-recovery - safe per the manual
            return;
        }

        List<OrderLine> lines = event.lines().stream()
                .map(line -> new OrderLine(line.sellerSku(), line.qty()))
                .toList();

        String decision;
        Map<String, Object> result;
        if (lines.stream().allMatch(this::isAvailable)) {
            result = orderService.placeOrder(lines);
            // placeOrder re-validates stock itself; a genuine race between
            // our check above and its own could still reject it.
            decision = OrderService.STATUS_CONFIRMED.equals(result.get("status")) ? "ACCEPTED" : "REJECTED";
        } else if (everyShortLineHasAnOpenSupplierOrder(lines)) {
            result = orderService.placeBackorder(lines);
            decision = "BACKORDERED";
        } else {
            // Couldn't confirm a genuinely open supplier PO for every
            // short line (most likely LegacySupply is unreachable right
            // now) - per the lab's own definitions that's REJECTED ("you
            // cannot fill it and have no restock coming"), not an
            // optimistic BACKORDERED. placeOrder() re-validates and
            // rejects each short line with its real reason, the same path
            // the "available" branch above already uses.
            result = orderService.placeOrder(lines);
            decision = "REJECTED";
        }

        Long shopOrderId = (Long) result.get("orderId");
        String channelStatus = toChannelStatus(decision);
        channelOrderRepository.save(new ChannelOrder(event.orderId(), shopOrderId, channelStatus));

        String reason = "REJECTED".equals(decision) ? (String) result.get("reason") : null;
        client.decide(event.orderId(), decision, "SO-" + shopOrderId, reason);
    }

    private void handleOrderCancelled(FeedEvent event) {
        ChannelOrder channelOrder = channelOrderRepository.findByTiangeOrderId(event.orderId()).orElse(null);
        if (channelOrder == null) {
            // We never saw this order placed (shouldn't happen if the feed
            // is in order, but the feed is at-least-once, not guaranteed-
            // ordered) - nothing to cancel on our side; still confirm so
            // Tiangge doesn't keep retrying a cancellation we can't act on.
            log.warn("Tiangge cancellation for unknown order {}, confirming anyway", event.orderId());
            client.confirmCancellation(event.orderId(), false);
            return;
        }

        boolean restocked;
        if (OrderService.STATUS_CANCELLED.equals(channelOrder.getStatus())) {
            restocked = false; // already cancelled - resend confirmation only, per the manual's own idempotency note
        } else if (OrderService.STATUS_BACKORDERED.equals(channelOrder.getStatus())) {
            orderService.cancelBackorder(channelOrder.getShopOrderId()); // nothing was ever reserved
            restocked = false;
        } else {
            orderService.cancelOrder(channelOrder.getShopOrderId()); // restocks + publishes StockChangedEvent
            restocked = true;
        }

        channelOrder.setStatus(OrderService.STATUS_CANCELLED);
        channelOrderRepository.save(channelOrder);
        client.confirmCancellation(event.orderId(), restocked);
    }

    private void resendDecision(ChannelOrder existing) {
        if (OrderService.STATUS_BACKORDERED.equals(existing.getStatus())) {
            // Tiangge's feed is at-least-once, so during an extended
            // LegacySupply outage the SAME stuck order gets redelivered
            // repeatedly - each redelivery is a free opportunity to retry
            // a PO that's still PENDING, instead of waiting on the
            // independent scheduled sweep alone. Doesn't change what
            // decision gets reported (still BACKORDERED either way) -
            // just gives a stuck row another chance to actually land.
            retryOpenSupplierOrders(existing.getShopOrderId());
        }

        String decision = switch (existing.getStatus()) {
            case OrderService.STATUS_CONFIRMED -> "ACCEPTED";
            case OrderService.STATUS_BACKORDERED -> "BACKORDERED";
            case OrderService.STATUS_CANCELLED -> "CANCELLED";
            default -> "REJECTED";
        };
        client.decide(existing.getTiangeOrderId(), decision, "SO-" + existing.getShopOrderId(), null);
    }

    private void retryOpenSupplierOrders(Long shopOrderId) {
        orderService.getAllOrders().stream()
                .filter(order -> order.getOrderId().equals(shopOrderId))
                .findFirst()
                .ifPresent(order -> order.getItems().forEach(item -> {
                    InventoryItem stockItem = inventoryService.getItem(item.getProductId());
                    int stock = stockItem == null ? 0 : stockItem.getStock();
                    if (item.getQuantity() > stock) {
                        // No-op if already genuinely open; retries the
                        // send if it's still PENDING - same logic
                        // requestReorder() already uses on first attempt.
                        supplierGateway.requestReorder(item.getProductId(), item.getQuantity());
                    }
                }));
    }

    private boolean isAvailable(OrderLine line) {
        InventoryItem item = inventoryService.getItem(line.productId());
        return item != null && line.quantity() <= item.getStock();
    }

    /**
     * Attempts to secure a supplier PO for every short line, and reports
     * whether every one of them actually ended up genuinely open.
     * requestReorder() returning empty means one was already open before
     * this call - good. A present result with status PENDING means
     * LegacySupply couldn't be reached just now for that line - not open
     * yet. Every short line gets an attempt regardless of an earlier
     * line's outcome, since each product's reorder is independent.
     */
    private boolean everyShortLineHasAnOpenSupplierOrder(List<OrderLine> lines) {
        boolean allOpen = true;
        for (OrderLine line : lines) {
            if (isAvailable(line)) {
                continue;
            }
            Optional<ReorderResult> result = supplierGateway.requestReorder(line.productId(), line.quantity());
            if (result.isPresent() && result.get().status() == SupplierOrderStatus.PENDING) {
                allOpen = false;
            }
        }
        return allOpen;
    }

    private static String toChannelStatus(String decision) {
        return switch (decision) {
            case "ACCEPTED" -> OrderService.STATUS_CONFIRMED;
            case "BACKORDERED" -> OrderService.STATUS_BACKORDERED;
            default -> OrderService.STATUS_REJECTED;
        };
    }
}