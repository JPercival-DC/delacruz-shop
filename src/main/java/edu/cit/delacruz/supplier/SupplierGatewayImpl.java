package edu.cit.delacruz.supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.supplier.SupplierProperties.SkuInfo;

/**
 * Called from {@link AutoReorderListener} through the {@link SupplierGateway}
 * bean, always from an AFTER_COMMIT context with no ambient transaction -
 * hence REQUIRES_NEW below. Relying on each repository call to open its
 * own implicit transaction (the original approach) turned out not to be
 * reliable from this specific caller: it surfaced as "No EntityManager
 * with actual transaction available" in testing, most likely because
 * Open-in-View leaves a non-transactional EntityManager bound to the same
 * request thread this callback runs on. REQUIRES_NEW forces a genuinely
 * fresh transaction regardless of what else is bound to the thread.
 * <p>
 * The lookup for an existing order below runs BEFORE any insert, not by
 * attempting the insert and catching a constraint violation after the
 * fact. Postgres aborts the *whole* transaction on a constraint
 * violation, not just the failed statement, so catching the exception
 * here and returning normally wouldn't help - the commit Spring's proxy
 * attempts right after this method returns would still fail against the
 * already-aborted transaction. The partial unique index in schema.sql
 * remains as a safety net for a genuine concurrent race (two threads
 * reordering the same product at the same instant); in that rare case
 * this method throws rather than degrading gracefully, which just means
 * that one attempt is logged and dropped rather than silently duplicated
 * - an acceptable trade for how unlikely that race is in this app.
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    // LegacySupply's own Qty ceiling (manual: "whole number from 1 to 99").
    private static final int MAX_CASES_PER_ORDER = 99;
    private static final List<SupplierOrderStatus> CLOSED_STATUSES =
            List.of(SupplierOrderStatus.DELIVERED, SupplierOrderStatus.FAILED);

    private final SupplierOrderRepository repository;
    private final SupplierProperties properties;
    private final SupplierOrderSender sender;

    SupplierGatewayImpl(SupplierOrderRepository repository, SupplierProperties properties, SupplierOrderSender sender) {
        this.repository = repository;
        this.properties = properties;
        this.sender = sender;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ReorderResult> requestReorder(String productId, int unitsNeeded) {
        if (unitsNeeded <= 0) {
            throw new IllegalArgumentException("unitsNeeded must be positive");
        }
        Optional<SupplierOrder> existing = repository.findFirstByProductIdAndStatusNotIn(productId, CLOSED_STATUSES);
        if (existing.isPresent()) {
            SupplierOrder order = existing.get();
            if (order.getStatus() != SupplierOrderStatus.PENDING) {
                // Genuinely open already (LegacySupply has acknowledged
                // it) - reserve() firing LowStockEvent again before that
                // clears is expected, not an error.
                log.debug("Reorder already open for {}, skipping", productId);
                return Optional.empty();
            }
            // Still PENDING: an earlier attempt for this same shortage
            // never actually reached LegacySupply. Retry sending THIS row
            // - same X-Request-Id/BuyerRef, never a second row for one
            // open shortage - instead of treating "a row exists in our
            // own DB" as "LegacySupply has it," which is what let orders
            // get reported BACKORDERED before a PO genuinely existed.
            sender.trySend(order);
            return Optional.of(new ReorderResult(order.getId(), productId, order.getUnits(), order.getStatus()));
        }

        SkuInfo sku = properties.lookup(productId);
        int cases = casesFor(unitsNeeded, sku.packSize());
        int units = cases * sku.packSize();

        SupplierOrder order = repository.save(
                new SupplierOrder(productId, UUID.randomUUID().toString(), cases, units));
        order.setBuyerRef("RO-" + order.getId()); // managed entity - dirty-checked and flushed at commit

        // Try to place it with LegacySupply right now instead of waiting up
        // to send-interval-ms (15s) for the next scheduled sweep. This is
        // specifically what a fresh Tiangge backorder needs: the caller
        // (AutoReorderListener, or the channel module backordering a short
        // Tiangge order) gets back an order that's either already PLACED,
        // or still safely PENDING for the scheduled job to retry - never a
        // decision reported as "a PO is open" before LegacySupply has
        // actually been asked. A product with an order already genuinely
        // open never reaches this line at all (handled above); a product
        // still stuck PENDING retries through the branch above instead of
        // creating a second row - this fresh-row path only runs once per
        // shortage episode, not once per order.
        //
        // ponytail: this call happens inside the REQUIRES_NEW transaction
        // above, so the DB connection for this transaction stays checked
        // out for however long LegacySupplyClient's retries take (bounded,
        // ~10-13s worst case). Under many simultaneous *fresh* shortages at
        // once this could pressure the connection pool. Upgrade path: move
        // the row-creation and the send into two separate steps (publish an
        // internal "order created" signal after the creation commits, send
        // from a non-transactional handler) if that pressure ever shows up
        // in practice.
        sender.trySend(order);

        return Optional.of(new ReorderResult(order.getId(), productId, units, order.getStatus()));
    }

    @Override
    public Optional<String> supplierSkuFor(String productId) {
        try {
            return Optional.of(properties.lookup(productId).sku());
        } catch (IllegalArgumentException notMapped) {
            return Optional.empty();
        }
    }

    /**
     * Rounds units up to whole cases, then caps at LegacySupply's own Qty
     * ceiling.
     * <p>
     * ponytail: caps a single reorder at 99 cases instead of splitting it
     * into multiple purchase orders. At the configured reorder-target this
     * never triggers (packSize >= 6, target defaults to 30, so at most 5
     * cases); revisit by splitting into multiple SupplierOrder rows if the
     * target grows enough to matter.
     */
    static int casesFor(int unitsNeeded, int packSize) {
        int cases = (unitsNeeded + packSize - 1) / packSize;
        return Math.min(cases, MAX_CASES_PER_ORDER);
    }
}