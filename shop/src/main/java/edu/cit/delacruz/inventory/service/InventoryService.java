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

    List<InventoryItem> getAllItems();

}
