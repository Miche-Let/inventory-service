package com.michelet.inventory.infrastructure.messaging.dto;

import java.util.UUID;

public record OrderRejectedEvent(UUID reservationId, String reason) {
    public OrderRejectedEvent {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId는 null일 수 없습니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("거절 사유(reason)는 필수입니다.");
        }
    }
}
