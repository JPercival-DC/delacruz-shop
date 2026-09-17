package edu.cit.delacruz.inventory.event;

/**
 * Published by the Inventory module whenever a successful reserve()
 * leaves a product's stock below the configured threshold. This class
 * (not InventoryService, not InventoryServiceImpl) is the only thing
 * the Notification module is allowed to depend on from Inventory.
 */
public final class LowStockEvent {

    private final String productId;
    private final String name;
    private final int remainingStock;
    private final int threshold;

    public LowStockEvent(String productId, String name, int remainingStock, int threshold) {
        this.productId = productId;
        this.name = name;
        this.remainingStock = remainingStock;
        this.threshold = threshold;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public int getThreshold() {
        return threshold;
    }
}
