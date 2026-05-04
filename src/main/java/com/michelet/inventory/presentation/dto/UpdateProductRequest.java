package com.michelet.inventory.presentation.dto;

import com.michelet.inventory.application.dto.UpdateProductCommand;
import com.michelet.inventory.domain.model.ProductCategory;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Map;

public record UpdateProductRequest(
    String name,
    ProductCategory category,
    @PositiveOrZero BigDecimal basePrice,
    Map<String, Object> attributes
) {
    public UpdateProductCommand toCommand() {
        return new UpdateProductCommand(name, category, basePrice, attributes);
    }
}
