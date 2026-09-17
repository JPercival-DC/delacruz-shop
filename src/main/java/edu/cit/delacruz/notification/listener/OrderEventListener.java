package edu.cit.delacruz.notification.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.delacruz.inventory.event.LowStockEvent;
import edu.cit.delacruz.notification.service.NotificationService;
import edu.cit.delacruz.shop.event.OrderCancelledEvent;
import edu.cit.delacruz.shop.event.OrderPlacedEvent;
import edu.cit.delacruz.shop.event.OrderRejectedEvent;

/**
 * The only thing in the Notification module that touches the other two
 * modules — and only their event classes (edu.cit.delacruz.shop.event,
 * edu.cit.delacruz.inventory.event), never OrderService or
 * InventoryService directly. Order and Inventory, in turn, never import
 * anything from edu.cit.delacruz.notification.
 * <p>
 * Deliberately NOT @Async. Plain @EventListener methods run synchronously,
 * on the same thread, inside whatever transaction the publisher (e.g.
 * OrderService.placeOrder) is already running in. For this lab that's the
 * right tradeoff: the notification row is guaranteed to exist by the time
 * the HTTP response for POST /api/orders returns, so the frontend's
 * "refresh the activity feed after every order" behavior never has to
 * poll or race a background thread to see it. The cost is that a slow or
 * failing notification write could, in principle, hold up or roll back
 * the order transaction — acceptable here since NotificationServiceImpl
 * does nothing but a single local insert, but worth flagging as the
 * thing you'd revisit (e.g. @TransactionalEventListener(AFTER_COMMIT) or
 * @Async with its own error handling) if Notification ever did anything
 * slower or less reliable, like calling an external API.
 */
@Component
public class OrderEventListener {

    private final NotificationService notificationService;

    public OrderEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationService.record(
                "ORDER_CONFIRMED",
                "Order O" + event.getOrderId() + " confirmed."
        );
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        notificationService.record(
                "ORDER_REJECTED",
                "Order O" + event.getOrderId() + " rejected: " + event.getReason()
        );
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.record(
                "ORDER_CANCELLED",
                "Order O" + event.getOrderId() + " cancelled; stock restored."
        );
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        notificationService.record(
                "LOW_STOCK",
                "Reorder needed: " + event.getName() + " (" + event.getProductId() + ") has only "
                        + event.getRemainingStock() + " left (threshold " + event.getThreshold() + ")."
        );
    }
}
