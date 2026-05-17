package com.michelet.inventory.infrastructure.messaging.dto;

import java.util.List;
import java.util.UUID;

public record OrderCreatedMessage(
    UUID eventId,
    UUID reservationId,
    List<OrderItemDto> items
) {
    public OrderCreatedMessage {
        if (eventId == null) {
            throw new IllegalArgumentException("메시지 파싱 오류: eventId는 null일 수 없습니다.");
        }
        if (reservationId == null) {
            throw new IllegalArgumentException("메시지 파싱 오류: reservationId는 null일 수 없습니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("메시지 파싱 오류: 주문 항목은 1개 이상이어야 합니다.");
        }
        // 리스트 내부 null 원소 검증
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("메시지 파싱 오류: 주문 항목에 null 값이 포함될 수 없습니다.");
        }

        // 외부 조작 방지를 위한 불변 리스트 복사
        items = List.copyOf(items);
    }

    public record OrderItemDto(UUID optionId, Integer quantity) {
        public OrderItemDto {
            if (optionId == null) {
                throw new IllegalArgumentException("메시지 파싱 오류: optionId는 null일 수 없습니다.");
            }
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("메시지 파싱 오류: 수량은 1 이상이어야 합니다.");
            }
        }
    }
}
