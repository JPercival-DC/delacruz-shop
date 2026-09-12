package edu.cit.delacruz.inventory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;

/**
 * The Inventory module's own public API (e.g. for an admin/stock view).
 * Note this also only depends on the InventoryService interface, never
 * the repository or InventoryServiceImpl — the boundary is enforced
 * even for callers inside the same module's package tree.
 */
@RestController
@RequestMapping("/api/inventory")
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
