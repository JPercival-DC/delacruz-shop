package edu.cit.delacruz.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
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

        return inventoryRepository.save(item);
    }
}
