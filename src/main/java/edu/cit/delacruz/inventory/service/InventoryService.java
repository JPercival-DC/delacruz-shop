package edu.cit.delacruz.inventory.service;

import java.util.List;

import edu.cit.delacruz.inventory.model.InventoryItem;

/**
 * The ONLY public contract of the Inventory module. The Order (shop)
 * module is only ever allowed to depend on this interface — never on
 * {@code InventoryRepository} or the package-private
 * {@code InventoryServiceImpl} directly.
 */
public interface InventoryService {

    /**
     * @return the item, or {@code null} if no product exists with that id.
     */
    InventoryItem getItem(String productId);

    /**
     * Attempts to reserve {@code quantity} units of {@code productId}.
     *
     * @throws InsufficientStockException if quantity exceeds current stock
     * @throws IllegalArgumentException   if the product does not exist or
     *                                     quantity is not positive
     */
    InventoryItem reserve(String productId, int quantity);

    /**
     * Reserves every line. Runs as a single transaction: if any line
     * fails (insufficient stock, missing product, bad quantity), every
     * reservation already made by this same call is rolled back — no
     * line is left partially reserved. Callers (Order) are expected to
     * have already validated each line before calling this, so a
     * failure here should only ever happen on a genuine concurrent
     * stock change between that check and this call.
     *
     * @throws InsufficientStockException on the first line that fails
     * @throws IllegalArgumentException   if any product does not exist
     *                                     or any quantity is not positive
     */
    List<InventoryItem> reserveAll(List<ReservationLine> lines);

    /**
     * Returns {@code quantity} units of {@code productId} to stock.
     * Used when an order is cancelled.
     *
     * @throws IllegalArgumentException if the product does not exist or
     *                                   quantity is not positive
     */
    InventoryItem restock(String productId, int quantity);

    List<InventoryItem> getAllItems();

}
