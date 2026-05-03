package com.michelet.inventory.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record RestoreStockRequest(
    @NotNull UUID optionId,
    @Positive int quantity
) {
}
