package com.michelet.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductCreatedEvent(
    UUID productId,
    UUID restaurantId,
    String name,
    String category,
    Map<String, Object> attributes,
    LocalDateTime startAt,
    LocalDateTime endAt,
    List<OptionEventDto> options
) {
    public record OptionEventDto(
        UUID optionId,
        String name,
        BigDecimal addPrice,
        Integer totalQuantity,
        Integer currentDailyStock
    ) {
    }
}
