package com.michelet.inventory.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReserveStockRequest(
    @NotNull(message = "optionId는 필수입니다.")
    UUID optionId,
    @NotNull(message = "quantity는 필수입니다.")
    @Min(value = 1, message = "quantity는 1 이상이어야 합니다.")
    Integer quantity,
    @NotNull(message = "멱등키(reservationId)는 필수입니다.")
    UUID reservationId
) {
}
