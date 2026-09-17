package edu.cit.delacruz.shop.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class OrderRequest {

    @NotEmpty(message = "items must contain at least one line")
    @Valid
    private List<OrderLineRequest> items;

    public OrderRequest() {
    }

    public List<OrderLineRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderLineRequest> items) {
        this.items = items;
    }
}
