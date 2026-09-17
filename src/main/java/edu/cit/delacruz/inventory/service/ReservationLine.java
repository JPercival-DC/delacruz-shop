package edu.cit.delacruz.inventory.service;

/**
 * One requested (productId, quantity) pair, used by
 * {@link InventoryService#reserveAll}. Part of the Inventory module's
 * public contract, same as {@link InsufficientStockException}.
 */
public record ReservationLine(String productId, int quantity) {
}
