package edu.cit.delacruz.channel;

import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
 * {@code @Async}: this used to call Tiangge synchronously, inline, on
 * whatever thread performed the commit - the web UI's Tomcat thread, or
 * the feed poller's scheduler thread. Under a flash-sale burst or a slow
 * Tiangge response (which the lab explicitly simulates), that queued
 * every later stock change behind however long the earlier ones took to
 * publish, which is how updates ended up arriving late. Moving the actual
 * HTTP call onto its own small pool lets the event fire-and-continue
 * instead of blocking order processing on a Tiangge round trip.
 * <p>
 * Re-reads current stock from InventoryService instead of trusting the
 * value captured on the event: once this runs asynchronously, two calls
 * for the same product can be reordered by the executor, so trusting a
 * stale captured number risks publishing an old value after a newer one.
 * Reading fresh at send time means whichever call actually runs last
 * reports the true current stock, regardless of queueing order.
 */
@Component
class TiangeStockListener {

    private static final Logger log = LoggerFactory.getLogger(TiangeStockListener.class);

    private final TiangeClient client;
    private final TiangeProperties properties;
    private final InventoryService inventoryService;

    TiangeStockListener(TiangeClient client, TiangeProperties properties, InventoryService inventoryService) {
        this.client = client;
        this.properties = properties;
        this.inventoryService = inventoryService;
    }

    @Bean
    Executor tiangeStockExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("tiangge-stock-");
        executor.initialize();
        return executor;
    }

    @Async("tiangeStockExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        if (!properties.listedProductIds().contains(event.getProductId())) {
            return;
        }
        InventoryItem current = inventoryService.getItem(event.getProductId());
        if (current == null) {
            return; // deleted between the event firing and this running - nothing to report
        }
        try {
            client.publishStock(List.of(new StockEntry(event.getProductId(), current.getStock())));
        } catch (RuntimeException e) {
            log.warn("Tiangge stock update for {} failed: {}", event.getProductId(), e.getMessage());
        }
    }
}
