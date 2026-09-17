package edu.cit.delacruz.shop.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.cit.delacruz.shop.model.Order;
import edu.cit.delacruz.shop.service.OrderCancellationException;
import edu.cit.delacruz.shop.service.OrderLine;
import edu.cit.delacruz.shop.service.OrderNotFoundException;
import edu.cit.delacruz.shop.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(@Valid @RequestBody OrderRequest request) {
        List<OrderLine> lines = new ArrayList<>();
        for (OrderLineRequest item : request.getItems()) {
            lines.add(new OrderLine(item.getProductId(), item.getQuantity()));
        }
        return ResponseEntity.ok(orderService.placeOrder(lines));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        try {
            return ResponseEntity.ok(orderService.cancelOrder(orderId));
        } catch (OrderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (OrderCancellationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public List<Order> getOrders() {
        return orderService.getAllOrders();
    }
}
