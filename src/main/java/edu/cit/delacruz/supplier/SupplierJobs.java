package edu.cit.delacruz.supplier;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.supplier.event.ReorderDeliveredEvent;

/**
 * The two background jobs Part D/E ask for: sending PENDING orders and
 * tracking open ones. Default Spring scheduling uses one thread, so these
 * two ticks can't overlap each other or themselves - no separate locking
 * needed to keep two ticks off the same row.
 */
@Component
class SupplierJobs {

    private static final Logger log = LoggerFactory.getLogger(SupplierJobs.class);
    private static final List<SupplierOrderStatus> OPEN_STATUSES = List.of(
            SupplierOrderStatus.PLACED, SupplierOrderStatus.PREPARING,
            SupplierOrderStatus.SHIPPED, SupplierOrderStatus.NEEDS_REVIEW);

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final SupplierOrderSender sender;
    private final ApplicationEventPublisher events;
    private final int batchSize;

    SupplierJobs(
            SupplierOrderRepository repository,
            LegacySupplyClient client,
            SupplierOrderSender sender,
            ApplicationEventPublisher events,
            @Value("${app.supplier.batch-size:5}") int batchSize
    ) {
        this.repository = repository;
        this.client = client;
        this.sender = sender;
        this.events = events;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.supplier.send-interval-ms:15000}")
    @Transactional
    public void sendPendingOrders() {
        List<SupplierOrder> pending = repository.findByStatus(
                SupplierOrderStatus.PENDING, PageRequest.of(0, batchSize, Sort.by("createdAt")));

        for (SupplierOrder order : pending) {
            if (sender.trySend(order) == SupplierOrderSender.Outcome.RATE_LIMITED) {
                log.info("LegacySupply quota hit, stopping this tick");
                break; // leave the rest PENDING; the next tick tries again
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.supplier.poll-interval-ms:30000}")
    @Transactional
    public void trackOpenOrders() {
        List<SupplierOrder> open = repository.findByStatusInAndPoNumberIsNotNull(
                OPEN_STATUSES, PageRequest.of(0, batchSize, Sort.by("updatedAt")));

        for (SupplierOrder order : open) {
            try {
                LegacyXml.OrderStatus status = client.getStatus(order.getPoNumber());
                applyStatus(order, mapStatus(status.statusCode()));
            } catch (LegacySupplyClient.RateLimitedException e) {
                log.info("LegacySupply quota hit, stopping this tick");
                break;
            } catch (LegacySupplyClient.PermanentException e) {
                // e.g. E-PO-04: the PO we're holding is gone server-side.
                // Flag it for a human instead of guessing what happened.
                log.warn("Could not check {}: {}", order.getPoNumber(), e.getMessage());
                order.setStatus(SupplierOrderStatus.NEEDS_REVIEW);
            } catch (RuntimeException e) {
                log.warn("Status check for {} failed, will retry next tick: {}",
                        order.getPoNumber(), e.getMessage());
            }
        }
    }

    private void applyStatus(SupplierOrder order, SupplierOrderStatus mapped) {
        // Only a known pipeline status (PLACED..DELIVERED) can be "behind"
        // another one; NEEDS_REVIEW/FAILED/PENDING aren't ranked, so a
        // NEEDS_REVIEW order can always move forward once it resolves, and
        // an out-of-order poll response can never walk a real order backwards.
        if (pipelineRank(mapped) != 0 && pipelineRank(mapped) < pipelineRank(order.getStatus())) {
            return;
        }
        order.setStatus(mapped);
        if (mapped == SupplierOrderStatus.DELIVERED) {
            events.publishEvent(new ReorderDeliveredEvent(order.getProductId(), order.getUnits()));
        }
    }

    private static int pipelineRank(SupplierOrderStatus status) {
        return switch (status) {
            case PLACED -> 1;
            case PREPARING -> 2;
            case SHIPPED -> 3;
            case DELIVERED -> 4;
            default -> 0;
        };
    }

    /** Maps LegacySupply's StatusCode to our enum. Unknown code -> NEEDS_REVIEW (see INTEGRATION.md, Part E). */
    static SupplierOrderStatus mapStatus(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.PLACED;
            case 20 -> SupplierOrderStatus.PREPARING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.NEEDS_REVIEW;
        };
    }
}
