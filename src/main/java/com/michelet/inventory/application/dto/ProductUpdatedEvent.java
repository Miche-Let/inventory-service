package com.michelet.inventory.application.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ProductUpdatedEvent(
    UUID productId,
    String name,
    String category,
    BigDecimal basePrice,
    Map<String, Object> attributes
) {
}
