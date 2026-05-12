package com.michelet.inventory.infrastructure.messaging.dto;

import java.util.UUID;

// 오더 서비스가 발행한 이벤트를 읽어들이기 위한 수신 전용 DTO
public record StockRestoreMessage(
    UUID optionId,
    Integer quantity
) {
    public StockRestoreMessage {
        if (optionId == null) {
            throw new IllegalArgumentException("재고 복구 이벤트 파싱 오류: optionId는 null일 수 없습니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("재고 복구 이벤트 파싱 오류: quantity는 1 이상이어야 합니다.");
        }
    }
}
