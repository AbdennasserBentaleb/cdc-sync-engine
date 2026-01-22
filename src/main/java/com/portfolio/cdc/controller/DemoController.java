package com.portfolio.cdc.controller;

import com.portfolio.cdc.entity.OrderEntity;
import com.portfolio.cdc.model.OrderDocument;
import com.portfolio.cdc.repository.OrderJpaRepository;
import com.portfolio.cdc.repository.OrderSearchRepository;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class DemoController {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderSearchRepository orderSearchRepository;

    public DemoController(OrderJpaRepository orderJpaRepository, OrderSearchRepository orderSearchRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderSearchRepository = orderSearchRepository;
    }

    @GetMapping("/source")
    public List<OrderEntity> getPostgresOrders() {
        return orderJpaRepository.findAll();
    }

    @GetMapping("/index")
    public List<OrderDocument> getElasticsearchOrders() {
        List<OrderDocument> docs = new ArrayList<>();
        orderSearchRepository.findAll().forEach(docs::add);
        return docs;
    }

    @PostMapping
    public OrderEntity createOrder(@RequestBody OrderEntity order) {
        return orderJpaRepository.save(order);
    }

    @PutMapping("/{id}")
    public OrderEntity updateOrderStatus(@PathVariable Integer id, @RequestBody OrderEntity orderDetails) {
        return orderJpaRepository.findById(id).map(order -> {
            order.setStatus(orderDetails.getStatus());
            if (orderDetails.getTotalAmount() != null) {
                order.setTotalAmount(orderDetails.getTotalAmount());
            }
            if (orderDetails.getCustomerId() != null) {
                order.setCustomerId(orderDetails.getCustomerId());
            }
            return orderJpaRepository.save(order);
        }).orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @DeleteMapping("/{id}")
    public void deleteOrder(@PathVariable Integer id) {
        orderJpaRepository.deleteById(id);
    }
}
