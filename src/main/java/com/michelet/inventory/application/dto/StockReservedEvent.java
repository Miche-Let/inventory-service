package com.michelet.inventory.application.dto;

import java.util.Objects;
import java.util.UUID;

public record StockReservedEvent(
    UUID optionId,
    Integer totalQuantity,
    Integer currentDailyStock
) {
    public StockReservedEvent {
        Objects.requireNonNull(optionId, "옵션 ID(optionId)는 필수입니다.");
        Objects.requireNonNull(totalQuantity, "총 재고 수량(totalQuantity)은 필수입니다.");
        Objects.requireNonNull(currentDailyStock, "일일 재고 수량(currentDailyStock)은 필수입니다.");
        if (totalQuantity < 0 || currentDailyStock < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
    }
}
