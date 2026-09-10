package edu.cit.delacruz.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.repository.InventoryRepository;

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

    InventoryItem item = inventoryRepository
            .findById(productId)
            .orElse(null);

    if (item == null) {
        return null;
    }

    if (quantity <= 0) {
        return item;
    }

    if (quantity > item.getStock()) {
        return item;
    }

    item.setStock(item.getStock() - quantity);

    return inventoryRepository.save(item);
}

}
