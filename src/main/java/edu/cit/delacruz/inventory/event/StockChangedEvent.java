package edu.cit.delacruz.inventory.event;

/**
 * Published every time a product's stock actually changes, whatever the
 * cause (a reservation, a cancellation, a supplier delivery). Deliberately
 * generic - this module has no idea a sales channel is listening for it,
 * same as how LowStockEvent predates and knows nothing about the supplier
 * module that now reacts to it.
 */
public final class StockChangedEvent {

    private final String productId;
    private final int newStock;

    public StockChangedEvent(String productId, int newStock) {
        this.productId = productId;
        this.newStock = newStock;
    }

    public String getProductId() {
        return productId;
    }

    public int getNewStock() {
        return newStock;
    }
}
