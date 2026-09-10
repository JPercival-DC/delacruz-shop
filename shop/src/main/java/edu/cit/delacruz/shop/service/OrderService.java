package edu.cit.delacruz.shop.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.inventory.model.InventoryItem;
import edu.cit.delacruz.inventory.service.InventoryService;
import edu.cit.delacruz.shop.model.Order;
import edu.cit.delacruz.shop.repository.OrderRepository;

@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(
            InventoryService inventoryService,
            OrderRepository orderRepository
    ) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Map<String, Object> placeOrder(String productId, int quantity) {

        Map<String, Object> response = new LinkedHashMap<>();

        if (productId == null || productId.isBlank()) {
            return saveRejectedOrder(
                    productId,
                    quantity,
                    "Product ID is required.",
                    null
            );
        }

        if (quantity <= 0) {
            return saveRejectedOrder(
                    productId,
                    quantity,
                    "Quantity must be greater than zero.",
                    null
            );
        }

        InventoryItem item = inventoryService.getItem(productId);

        if (item == null) {
            return saveRejectedOrder(
                    productId,
                    quantity,
                    "Product not found.",
                    null
            );
        }

        if (quantity > item.getStock()) {
            return saveRejectedOrder(
                    productId,
                    quantity,
                    "Insufficient stock.",
                    item
            );
        }

        InventoryItem updatedItem =
                inventoryService.reserve(productId, quantity);

        Order order = new Order(
                productId,
                quantity,
                "CONFIRMED",
                "Order confirmed.",
                LocalDateTime.now()
        );

        orderRepository.save(order);

        response.put("status", "CONFIRMED");
        response.put("reason", "Order confirmed.");
        response.put("inventory", updatedItem);

        return response;
    }

    private Map<String, Object> saveRejectedOrder(
            String productId,
            int quantity,
            String reason,
            InventoryItem inventory
    ) {
        Order order = new Order(
                productId,
                quantity,
                "REJECTED",
                reason,
                LocalDateTime.now()
        );

        orderRepository.save(order);

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("status", "REJECTED");
        response.put("reason", reason);
        response.put("inventory", inventory);

        return response;
    }
}