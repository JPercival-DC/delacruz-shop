package edu.cit.delacruz.inventory.service;

/**
 * Thrown by {@link InventoryService#reserve(String, int)} when the
 * requested quantity exceeds the current stock for a product. This is
 * part of the public contract of the Inventory module (it crosses the
 * module boundary), so it lives next to the interface, not the impl.
 */
public class InsufficientStockException extends RuntimeException {

    private final String productId;
    private final int requestedQuantity;
    private final int availableStock;

    public InsufficientStockException(String productId, int requestedQuantity, int availableStock) {
        super("Insufficient stock for " + productId + ": requested " + requestedQuantity
                + " but only " + availableStock + " available.");
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableStock = availableStock;
    }

    public String getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableStock() {
        return availableStock;
    }
}
