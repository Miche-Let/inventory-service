package com.michelet.inventory.presentation.dto;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.domain.model.ProductCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductRequest(
    UUID restaurantId,
    String name,
    ProductCategory category,
    BigDecimal basePrice,
    Map<String, Object> attributes,
    ExhibitionRequest exhibition,
    List<OptionRequest> options
) {
    public record ExhibitionRequest(LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record OptionRequest(String name, BigDecimal addPrice, Integer totalQuantity, Integer dailyLimit,
                                Integer maxLimit) {
    }

    public CreateProductCommand toCommand() {
        return new CreateProductCommand(
            restaurantId, name, category, basePrice, attributes,
            new CreateProductCommand.ExhibitionCommand(exhibition.startAt(), exhibition.endAt()),
            options.stream().map(opt -> new CreateProductCommand.OptionCommand(
                opt.name(), opt.addPrice(), opt.totalQuantity(), opt.dailyLimit(), opt.maxLimit()
            )).toList()
        );
    }
}
