package edu.cit.delacruz.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.delacruz.channel.TiangeJson.StockEntry;
import edu.cit.delacruz.inventory.event.StockChangedEvent;
import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;

/**
 * Task 3: "any change in Inventory... publishes the new available quantity
 * to Tiangge within 30 seconds... do not publish on a timer." AFTER_COMMIT
 * so a reservation that later rolls back never reports stock it didn't
 * actually take; only products Tiangge actually lists are worth telling.
 * <p>
 * Dispatch is per-product via {@link PerProductSerialDispatcher}, not a
 * shared thread pool. A shared pool let two updates for the SAME product
 * run concurrently with no ordering guarantee between them, so a slower
 * call carrying an older value could complete (and reach Tiangge) after a
 * faster call carrying a newer one - Tiangge would then show the stale
 * number, looking exactly like an accepted order's stock change was
 * ignored. Per-product dispatch makes that impossible: updates for one
 * product run strictly in the order they were triggered, one at a time,
 * never concurrently with each other. Every individual change gets its
 * own publish - nothing is skipped or merged, since "an accepted order's
 * stock change was ignored" means exactly that: one specific change's
 * update never arrived, not just that the final number was eventually
 * right. Different products still run fully in parallel, so a backlog on
 * one product never delays another's updates.
 */
@Component
class TiangeStockListener {

    private static final Logger log = LoggerFactory.getLogger(TiangeStockListener.class);

    private final TiangeClient client;
    private final TiangeProperties properties;
    private final InventoryService inventoryService;
    private final PerProductSerialDispatcher dispatcher = new PerProductSerialDispatcher();

    TiangeStockListener(TiangeClient client, TiangeProperties properties, InventoryService inventoryService) {
        this.client = client;
        this.properties = properties;
        this.inventoryService = inventoryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        String productId = event.getProductId();
        if (!properties.listedProductIds().contains(productId)) {
            return;
        }
        // submit() returns almost immediately (a couple of atomic ops,
        // maybe an enqueue) - this still doesn't block the committing
        // thread on a Tiangge round trip, same goal the old @Async had,
        // but now with per-product ordering guaranteed instead of not.
        dispatcher.submit(productId, () -> publish(productId));
    }

    private void publish(String productId) {
        InventoryItem current = inventoryService.getItem(productId);
        if (current == null) {
            return; // deleted since the triggering change - nothing to report
        }
        try {
            client.publishStock(List.of(new StockEntry(productId, current.getStock())));
        } catch (RuntimeException e) {
            log.warn("Tiangge stock update for {} failed: {}", productId, e.getMessage());
        }
    }
}