package edu.cit.delacruz.shop.event;

/**
 * Published after an order is created BACKORDERED and saved - nothing was
 * reserved yet. See OrderPlacedEvent.
 */
public final class OrderBackorderedEvent {

    private final Long orderId;

    public OrderBackorderedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
