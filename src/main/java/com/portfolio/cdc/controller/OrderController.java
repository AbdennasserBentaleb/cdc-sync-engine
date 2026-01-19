package com.portfolio.cdc.controller;

import com.portfolio.cdc.entity.OrderEntity;
import com.portfolio.cdc.model.OrderDocument;
import com.portfolio.cdc.repository.OrderJpaRepository;
import com.portfolio.cdc.repository.OrderSearchRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for demonstrating the CDC pipeline end-to-end.
 *
 * <p>Creates orders in the PostgreSQL write model ({@code /source}) and
 * queries them from the Elasticsearch read model ({@code /index}), proving
 * that the Debezium CDC pipeline keeps the two stores eventually consistent.
 *
 * <p>Uses a proper {@link CreateOrderRequest} DTO — the internal JPA entity
 * is never exposed as an API contract.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Write-model API — mutations flow through CDC into Elasticsearch")
public class OrderController {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderSearchRepository orderSearchRepository;

    public OrderController(OrderJpaRepository orderJpaRepository,
                           OrderSearchRepository orderSearchRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderSearchRepository = orderSearchRepository;
    }

    @GetMapping("/source")
    @Operation(
            summary = "List orders from PostgreSQL (write model)",
            description = "Returns all orders directly from the source PostgreSQL database.",
            responses = @ApiResponse(responseCode = "200", description = "Orders retrieved from write model")
    )
    public ResponseEntity<List<OrderEntity>> getPostgresOrders() {
        return ResponseEntity.ok(orderJpaRepository.findAll());
    }

    @GetMapping("/index")
    @Operation(
            summary = "List orders from Elasticsearch (read model)",
            description = "Returns all orders from the Elasticsearch index. " +
                    "Content may lag PostgreSQL by a few seconds (eventual consistency window).",
            responses = @ApiResponse(responseCode = "200", description = "Orders retrieved from read model")
    )
    public ResponseEntity<List<OrderDocument>> getElasticsearchOrders() {
        List<OrderDocument> docs = new ArrayList<>();
        orderSearchRepository.findAll().forEach(docs::add);
        return ResponseEntity.ok(docs);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create a new order",
            description = "Creates an order in PostgreSQL. The Debezium CDC pipeline will " +
                    "asynchronously replicate the insertion to Elasticsearch within seconds.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order created"),
                    @ApiResponse(responseCode = "400", description = "Validation failed")
            }
    )
    public ResponseEntity<OrderEntity> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderEntity entity = new OrderEntity();
        entity.setCustomerId(request.customerId());
        entity.setTotalAmount(request.totalAmount());
        entity.setStatus(request.status());
        OrderEntity saved = orderJpaRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update order status",
            description = "Updates an order's status in PostgreSQL. The change is streamed to Elasticsearch via CDC.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order updated"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    public ResponseEntity<OrderEntity> updateOrderStatus(
            @PathVariable Integer id,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderEntity order = orderJpaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        order.setStatus(request.status());
        if (request.totalAmount() != null) order.setTotalAmount(request.totalAmount());
        if (request.customerId() != null)  order.setCustomerId(request.customerId());
        return ResponseEntity.ok(orderJpaRepository.save(order));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete an order",
            description = "Deletes an order from PostgreSQL. Debezium captures the DELETE event and removes it from Elasticsearch.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order deleted"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    public ResponseEntity<Void> deleteOrder(@PathVariable Integer id) {
        if (!orderJpaRepository.existsById(id)) {
            throw new EntityNotFoundException("Order not found: " + id);
        }
        orderJpaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
