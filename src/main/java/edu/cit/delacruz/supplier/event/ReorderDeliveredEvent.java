package edu.cit.delacruz.supplier.event;

/**
 * Published once a purchase order's LegacySupply status maps to
 * DELIVERED. This class - not SupplierOrder, not SupplierGateway - is the
 * only thing the Inventory module is allowed to depend on from the
 * supplier module, mirroring how Notification only depends on Order's
 * and Inventory's own event classes.
 */
public final class ReorderDeliveredEvent {

    private final String productId;
    private final int units;

    public ReorderDeliveredEvent(String productId, int units) {
        this.productId = productId;
        this.units = units;
    }

    public String getProductId() {
        return productId;
    }

    public int getUnits() {
        return units;
    }
}
