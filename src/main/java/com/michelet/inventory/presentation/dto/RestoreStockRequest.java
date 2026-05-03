package com.michelet.inventory.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RestoreStockRequest(
    @NotNull UUID optionId,
    @Positive int quantity,
    UUID reservationId   // 멱등키 — TODO 추후 중복 복구 방지 로직에서 활용 예정 (그땐 @NotNull)
) {
}
