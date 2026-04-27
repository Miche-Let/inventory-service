package com.michelet.inventory.presentation.dto;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.domain.model.ProductCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CreateProductRequest(
    @NotNull UUID restaurantId,
    @NotBlank String name,
    @NotNull ProductCategory category,
    @PositiveOrZero BigDecimal basePrice,
    Map<String, Object> attributes,
    @NotNull @Valid ExhibitionRequest exhibition,
    @NotEmpty List<@NotNull @Valid OptionRequest> options
) {
    public record ExhibitionRequest(@NotNull LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record OptionRequest(
        @NotBlank String name,
        @PositiveOrZero BigDecimal addPrice,
        @Min(0) Integer totalQuantity,
        @Min(0) Integer dailyLimit,
        @Min(0) Integer maxLimit
    ) {
    }

    public CreateProductCommand toCommand() {
        // Fail-Fast: DTO 변환 시점에 원천 차단
        Objects.requireNonNull(exhibition, "전시 정보는 필수입니다.");
        if (options == null || options.isEmpty() || options.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("옵션 정보(options)는 최소 1개 이상 필요합니다.");
        }

        return new CreateProductCommand(
            restaurantId, name, category, basePrice, attributes,
            new CreateProductCommand.ExhibitionCommand(exhibition.startAt(), exhibition.endAt()),
            options.stream().map(opt -> new CreateProductCommand.OptionCommand(
                opt.name(), opt.addPrice(), opt.totalQuantity(), opt.dailyLimit(), opt.maxLimit()
            )).toList()
        );
    }
}
