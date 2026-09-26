package edu.cit.delacruz.inventory.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.delacruz.inventory.service.InventoryService;
import edu.cit.delacruz.supplier.event.ReorderDeliveredEvent;

/**
 * The only place in Inventory that knows the supplier module exists - and
 * only its event class, never SupplierGateway or anything LegacySupply-
 * specific. Mirrors how Notification only depends on Order's and
 * Inventory's own event classes.
 * <p>
 * Plain (non-transactional-phase) @EventListener on purpose: this runs
 * inside SupplierJobs.trackOpenOrders()'s own transaction, so the status
 * flip to DELIVERED and this restock commit or roll back together.
 */
@Component
class SupplierDeliveryListener {

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onReorderDelivered(ReorderDeliveredEvent event) {
        inventoryService.restock(event.getProductId(), event.getUnits());
    }
}
