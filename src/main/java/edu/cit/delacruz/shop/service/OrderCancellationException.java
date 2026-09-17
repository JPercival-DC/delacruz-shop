package edu.cit.delacruz.shop.service;

/**
 * Thrown by {@link OrderService#cancelOrder} when the order exists but is
 * not in a cancellable state — already CANCELLED, or never CONFIRMED in
 * the first place (-> 409).
 */
public class OrderCancellationException extends RuntimeException {
    public OrderCancellationException(String message) {
        super(message);
    }
}
