package edu.cit.delacruz.shop.event;

/** Published after an order is rejected and saved. See OrderPlacedEvent. */
public final class OrderRejectedEvent {

    private final Long orderId;
    private final String reason;

    public OrderRejectedEvent(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }
}
