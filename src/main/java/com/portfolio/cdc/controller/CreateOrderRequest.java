package com.portfolio.cdc.controller;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request DTO for creating a new Order.
 * Decouples the API contract from the internal JPA entity model.
 */
public record CreateOrderRequest(
        @NotBlank(message = "customerId must not be blank")
        String customerId,

        @NotNull(message = "totalAmount must not be null")
        @DecimalMin(value = "0.0", inclusive = false, message = "totalAmount must be positive")
        BigDecimal totalAmount,

        @NotBlank(message = "status must not be blank")
        String status
) {}
