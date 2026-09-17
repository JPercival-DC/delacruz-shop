package edu.cit.delacruz.inventory.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.inventory.event.LowStockEvent;
import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.repository.InventoryRepository;

/**
 * Package-private on purpose: nothing outside edu.cit.delacruz.inventory
 * can reference this class directly, including the compiler. Callers
 * (Order/shop module included) are forced to depend on the
 * {@link InventoryService} interface instead, which is the enforced
 * module boundary for this exercise.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold
    ) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElse(null);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional
    public InventoryItem reserve(String productId, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        InventoryItem item = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // The rejection rule lives here, inside the Inventory module, not
        // in the caller — Order only ever sees the outcome via the
        // interface (a normal return, or InsufficientStockException).
        if (quantity > item.getStock()) {
            throw new InsufficientStockException(productId, quantity, item.getStock());
        }

        item.setStock(item.getStock() - quantity);
        InventoryItem saved = inventoryRepository.save(item);

        // Low-stock rule: fires on every successful reserve(), whether it
        // was called directly or as part of reserveAll() below, since
        // reserveAll() just calls this method in a loop.
        if (saved.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(
                    new LowStockEvent(saved.getProductId(), saved.getName(), saved.getStock(), lowStockThreshold));
        }

        return saved;
    }

    @Override
    @Transactional
    public List<InventoryItem> reserveAll(List<ReservationLine> lines) {
        List<InventoryItem> results = new ArrayList<>();
        for (ReservationLine line : lines) {
            // Self-invocation note: this is a plain internal method call,
            // not a call through the Spring proxy, so reserve()'s own
            // @Transactional annotation has no separate effect here — but
            // that's fine, because reserveAll() itself is transactional
            // and was entered through the proxy (Order calls it via the
            // injected InventoryService interface). Every reserve() below
            // therefore runs inside that same, single transaction: if any
            // one of them throws, everything this loop already did rolls
            // back together with it.
            results.add(reserve(line.productId(), line.quantity()));
        }
        return results;
    }

    @Override
    @Transactional
    public InventoryItem restock(String productId, int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        InventoryItem item = inventoryRepository
                .findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        item.setStock(item.getStock() + quantity);

        return inventoryRepository.save(item);
    }
}
