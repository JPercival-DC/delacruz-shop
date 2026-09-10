package edu.cit.delacruz.shop.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.repository.InventoryRepository;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    private final InventoryRepository inventoryRepository;

    public ProductController(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping
    public List<InventoryItem> getProducts() {
        return inventoryRepository.findAll();
    }
}
