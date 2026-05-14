package com.michelet.inventory.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReserveStockRequest(
    @NotNull UUID optionId,
    @NotNull @Min(1) Integer quantity,
    UUID reservationId   // 멱등키 — TODO 추후 동기 통신(Feign)에서의 재시도로 인한 중복 차감을 막기 위한 로직에서 활용 예정 (그땐 @NotNull)
) {
}
