package edu.cit.delacruz.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.cit.delacruz.supplier.SupplierProperties.SkuInfo;

/**
 * The one place that actually calls LegacySupply to place a purchase
 * order for a PENDING SupplierOrder row, and applies whatever happened
 * back onto that row's fields. Used by two callers: the scheduled sweep
 * (SupplierJobs, for PENDING rows a previous attempt left behind) and
 * immediately, once, right after a row is created (SupplierGatewayImpl) -
 * so a fresh reorder doesn't have to wait for the next scheduled tick
 * before it's actually placed with LegacySupply. Extracted here instead
 * of duplicated so both callers apply LegacySupply's response the same
 * way.
 * <p>
 * Never saves anything itself - only mutates the fields of the SupplierOrder
 * passed in. Callers are responsible for persisting it, since the two
 * callers have different transaction shapes (a batch loop vs. one row).
 */
@Component
class SupplierOrderSender {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderSender.class);

    private final LegacySupplyClient client;
    private final SupplierProperties properties;

    SupplierOrderSender(LegacySupplyClient client, SupplierProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    enum Outcome {
        /** LegacySupply accepted it; order.poNumber and order.status are now set. */
        PLACED,
        /** LegacySupply rejected it for good (our bug, not a hiccup); order.status is now FAILED. */
        REJECTED,
        /** Quota hit - stop trying more orders this tick, not just this one. */
        RATE_LIMITED,
        /** Timeout/503/retries exhausted - still PENDING, try again later. */
        RETRY_LATER
    }

    Outcome trySend(SupplierOrder order) {
        try {
            SkuInfo sku = properties.lookup(order.getProductId());
            LegacyXml.OrderAck ack = client.placeOrder(
                    sku.sku(), order.getCases(), order.getBuyerRef(), order.getRequestId());
            order.setPoNumber(ack.poNumber());
            order.setStatus(SupplierJobs.mapStatus(ack.statusCode()));
            return Outcome.PLACED;
        } catch (LegacySupplyClient.RateLimitedException e) {
            log.info("LegacySupply quota hit, leaving {} PENDING", order.getRequestId());
            return Outcome.RATE_LIMITED;
        } catch (LegacySupplyClient.PermanentException e) {
            log.warn("Reorder {} rejected by LegacySupply: {}", order.getRequestId(), e.getMessage());
            order.setStatus(SupplierOrderStatus.FAILED);
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            // Transient (timeout/503/retries exhausted): leave PENDING.
            // The outbox row is safe and the next attempt retries with the
            // same X-Request-Id/BuyerRef, so LegacySupply can't double-book it.
            log.warn("Reorder {} not sent, will retry: {}", order.getRequestId(), e.getMessage());
            return Outcome.RETRY_LATER;
        }
    }
}
