package edu.cit.delacruz.shop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import edu.cit.delacruz.shop.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
}