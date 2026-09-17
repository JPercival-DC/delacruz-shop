package edu.cit.delacruz.shop.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;

/**
 * Lives in the Order (shop) module but must only ever talk to Inventory
 * through the {@link InventoryService} interface — never the repository
 * or the (package-private) InventoryServiceImpl directly. This is the
 * enforced in-process module boundary.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final InventoryService inventoryService;

    public ProductController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryItem> getProducts() {
        return inventoryService.getAllItems();
    }
}
