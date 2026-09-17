package edu.cit.delacruz.shop.event;

/**
 * Published after an order is cancelled and its stock restored. Not
 * required by the lab spec's notification scenarios, but included so
 * cancellation shows up in the activity feed the same way confirm/reject
 * do, using the same event-driven mechanism.
 */
public final class OrderCancelledEvent {

    private final Long orderId;

    public OrderCancelledEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
