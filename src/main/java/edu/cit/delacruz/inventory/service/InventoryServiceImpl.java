package edu.cit.delacruz.inventory.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.inventory.event.LowStockEvent;
import edu.cit.delacruz.inventory.event.StockChangedEvent;
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

        // One atomic UPDATE ... WHERE stock >= quantity instead of a
        // separate read-check-write: that old sequence let two concurrent
        // reserve() calls for the same product both read the same stock
        // value, both pass the check, and both write — the second save()
        // silently overwriting the first (a lost update, which is how
        // overselling actually happened under real concurrent load). The
        // rejection rule still lives here, inside Inventory — Order only
        // ever sees the outcome via the interface (a normal return, or
        // InsufficientStockException) — but the DB itself now enforces it
        // atomically, so there's no gap for a second caller to land in.
        int rowsUpdated = inventoryRepository.decrementStock(productId, quantity);
        if (rowsUpdated == 0) {
            InventoryItem current = inventoryRepository.findById(productId).orElse(null);
            if (current == null) {
                throw new IllegalArgumentException("Product not found: " + productId);
            }
            throw new InsufficientStockException(productId, quantity, current.getStock());
        }

        InventoryItem saved = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product vanished mid-reservation: " + productId));

        eventPublisher.publishEvent(new StockChangedEvent(saved.getProductId(), saved.getStock()));

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

        // Same reasoning as reserve(): an atomic UPDATE instead of
        // read-then-write, so two concurrent restocks for the same
        // product (e.g. a cancellation and a supplier delivery landing at
        // once) can't lose one of them to a last-write-wins race.
        int rowsUpdated = inventoryRepository.incrementStock(productId, quantity);
        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        InventoryItem saved = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Product vanished mid-restock: " + productId));

        eventPublisher.publishEvent(new StockChangedEvent(saved.getProductId(), saved.getStock()));

        return saved;
    }
}
