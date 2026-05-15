package com.michelet.inventory.infrastructure.messaging.dto;

import java.util.UUID;

public record OrderApprovedEvent(UUID reservationId) {
    public OrderApprovedEvent {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId는 null일 수 없습니다.");
        }
    }
}
