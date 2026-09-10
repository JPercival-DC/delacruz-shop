package edu.cit.delacruz.inventory.service;

import java.util.List;

import edu.cit.delacruz.inventory.model.InventoryItem;

public interface InventoryService {

InventoryItem getItem(String productId);

InventoryItem reserve(String productId, int quantity);

List<InventoryItem> getAllItems();

}
