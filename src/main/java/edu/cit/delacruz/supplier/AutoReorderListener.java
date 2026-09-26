package edu.cit.delacruz.supplier;

import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;

import edu.cit.delacruz.inventory.event.LowStockEvent;

/**
 * The auto-reorder rule Part C asks for: "the auto-reorder rule calls
 * SupplierGateway instead of logging." A separate bean from
 * SupplierGatewayImpl, calling requestReorder() only through the injected
 * SupplierGateway interface - never by one method calling another inside
 * the same class, since that self-invocation would bypass any
 * {@code @Transactional} SupplierGatewayImpl adds later without warning.
 * <p>
 * AFTER_COMMIT on purpose: fires once the stock change that caused the low
 * reading is actually committed, not while Order's or Inventory's own
 * transaction is still open. That keeps a reservation that later gets
 * rolled back from ever creating a phantom purchase order.
 */
@Component
class AutoReorderListener {

    private final SupplierGateway supplierGateway;
    private final SupplierProperties properties;

    AutoReorderListener(SupplierGateway supplierGateway, SupplierProperties properties) {
        this.supplierGateway = supplierGateway;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        int unitsNeeded = Math.max(properties.reorderTarget() - event.getRemainingStock(), 1);
        supplierGateway.requestReorder(event.getProductId(), unitsNeeded);
    }
}
