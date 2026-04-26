package com.michelet.inventory.application.dto;

import com.michelet.inventory.domain.model.ProductCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductCommand(
    UUID restaurantId,
    String name,
    ProductCategory category,
    BigDecimal basePrice,
    Map<String, Object> attributes,
    ExhibitionCommand exhibition,
    List<OptionCommand> options
) {
    public record ExhibitionCommand(LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record OptionCommand(String name, BigDecimal addPrice, Integer totalQuantity, Integer dailyLimit,
                                Integer maxLimit) {
    }
}
