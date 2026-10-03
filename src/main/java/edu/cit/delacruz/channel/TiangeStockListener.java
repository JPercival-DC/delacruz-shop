package edu.cit.delacruz.channel;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;

import edu.cit.delacruz.channel.TiangeJson.StockEntry;
import edu.cit.delacruz.inventory.event.StockChangedEvent;

/**
 * Task 3: "any change in Inventory... publishes the new available quantity
 * to Tiangge within 30 seconds... do not publish on a timer." AFTER_COMMIT
 * so a reservation that later rolls back never reports stock it didn't
 * actually take; only products Tiangge actually lists are worth telling.
 */
@Component
class TiangeStockListener {

    private static final Logger log = LoggerFactory.getLogger(TiangeStockListener.class);

    private final TiangeClient client;
    private final TiangeProperties properties;

    TiangeStockListener(TiangeClient client, TiangeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        if (!properties.listedProductIds().contains(event.getProductId())) {
            return;
        }
        try {
            client.publishStock(List.of(new StockEntry(event.getProductId(), event.getNewStock())));
        } catch (RuntimeException e) {
            log.warn("Tiangge stock update for {} failed: {}", event.getProductId(), e.getMessage());
        }
    }
}
