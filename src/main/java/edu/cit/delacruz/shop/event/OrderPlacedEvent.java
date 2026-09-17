package edu.cit.delacruz.shop.event;

/**
 * Published after an order is confirmed and saved. This class (not
 * OrderService, not OrderRepository) is the only thing the Notification
 * module is allowed to depend on from the Order/shop module.
 */
public final class OrderPlacedEvent {

    private final Long orderId;

    public OrderPlacedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
