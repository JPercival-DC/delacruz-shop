package edu.cit.delacruz.shop.service;

/** One requested (productId, quantity) pair from an incoming order request. */
public record OrderLine(String productId, int quantity) {
}
