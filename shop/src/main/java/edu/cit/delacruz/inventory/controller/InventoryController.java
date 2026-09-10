package edu.cit.delacruz.inventory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "http://localhost:5173")
public class InventoryController {

private final InventoryService inventoryService;

public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
}

@GetMapping
public List<InventoryItem> getAllInventory() {
    return inventoryService.getAllItems();
}

}
