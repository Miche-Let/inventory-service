package com.michelet.inventory.presentation.dto;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.domain.model.ProductCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductRequest(
    @NotNull UUID restaurantId,
    @NotNull String name,
    @NotNull ProductCategory category,
    @NotNull BigDecimal basePrice,
    Map<String, Object> attributes,
    @NotNull @Valid ExhibitionRequest exhibition,
    @NotNull @Valid List<OptionRequest> options
) {
    public record ExhibitionRequest(@NotNull LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record OptionRequest(@NotNull String name, @NotNull BigDecimal addPrice, @NotNull Integer totalQuantity,
                                Integer dailyLimit,
                                Integer maxLimit) {
    }

    public CreateProductCommand toCommand() {
        // null 방어 로직 추가
        var exhibitionCommand = (exhibition != null)
            ? new CreateProductCommand.ExhibitionCommand(exhibition.startAt(), exhibition.endAt())
            : null;

        return new CreateProductCommand(
            restaurantId, name, category, basePrice, attributes,
            exhibitionCommand,
            options != null ? options.stream().map(opt -> new CreateProductCommand.OptionCommand(
                opt.name(), opt.addPrice(), opt.totalQuantity(), opt.dailyLimit(), opt.maxLimit()
            )).toList() : List.of()
        );
    }
}
