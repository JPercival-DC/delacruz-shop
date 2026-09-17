package edu.cit.delacruz.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.cit.delacruz.inventory.model.InventoryItem;

public interface InventoryRepository
        extends JpaRepository<InventoryItem, String> {
}