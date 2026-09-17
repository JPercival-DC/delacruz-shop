package edu.cit.delacruz.shop.service;

/** Thrown by {@link OrderService#cancelOrder} when the order id doesn't exist (-> 404). */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
    }
}
