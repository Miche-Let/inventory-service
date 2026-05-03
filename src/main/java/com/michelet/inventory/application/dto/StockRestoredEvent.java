package com.michelet.inventory.application.dto;

import java.util.Objects;
import java.util.UUID;

public record StockRestoredEvent(
    UUID optionId,
    Integer totalQuantity,
    Integer currentDailyStock
) {
    // 컴팩트 생성자
    public StockRestoredEvent {
        Objects.requireNonNull(optionId, "optionId는 필수입니다.");
        Objects.requireNonNull(totalQuantity, "totalQuantity는 필수입니다.");
        Objects.requireNonNull(currentDailyStock, "currentDailyStock는 필수입니다.");

        if (totalQuantity < 0 || currentDailyStock < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
    }
}
