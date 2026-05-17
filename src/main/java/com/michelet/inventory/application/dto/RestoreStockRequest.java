package com.michelet.inventory.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RestoreStockRequest(
    @NotNull UUID optionId,
    @Positive int quantity,
    @NotNull UUID eventId   // 멱등키 필드
) {
}
