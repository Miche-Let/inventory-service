package com.michelet.inventory.application.dto;

import com.michelet.inventory.domain.model.ProductCategory;
import java.math.BigDecimal;
import java.util.Map;

public record UpdateProductCommand(
    String name,
    ProductCategory category,
    BigDecimal basePrice,
    Map<String, Object> attributes
) {
}
