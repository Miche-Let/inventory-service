package com.michelet.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ProductCreatedEvent(
    UUID productId,
    UUID restaurantId,
    String name,
    String category,
    BigDecimal basePrice,
    Map<String, Object> attributes,
    LocalDateTime startAt,
    LocalDateTime endAt,
    List<OptionEventDto> options
) {
    public ProductCreatedEvent {
        Objects.requireNonNull(productId, "productId는 필수입니다.");
        Objects.requireNonNull(restaurantId, "restaurantId는 필수입니다.");
        Objects.requireNonNull(name, "name은 필수입니다.");
        Objects.requireNonNull(category, "category는 필수입니다.");

        // basePrice 과거 메시지(Null) 호환성 확보 및 음수 검증
        if (basePrice != null && basePrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("기본 가격은 0원 이상이어야 합니다.");
        }

        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record OptionEventDto(
        UUID optionId,
        String name,
        BigDecimal addPrice,
        Integer totalQuantity,
        Integer currentDailyStock
    ) {
        public OptionEventDto {
            Objects.requireNonNull(optionId, "optionId는 필수입니다.");
            Objects.requireNonNull(name, "옵션명은 필수입니다.");
            Objects.requireNonNull(addPrice, "addPrice는 필수입니다.");
            Objects.requireNonNull(totalQuantity, "totalQuantity는 필수입니다.");
            Objects.requireNonNull(currentDailyStock, "currentDailyStock는 필수입니다.");

            if (totalQuantity < 0 || currentDailyStock < 0) {
                throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
            }
        }
    }
}
