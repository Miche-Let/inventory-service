package com.michelet.inventory.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReserveStockRequest(
    @NotNull UUID optionId,
    @NotNull @Min(1) Integer quantity
) {
}
