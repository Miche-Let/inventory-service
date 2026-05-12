package com.michelet.inventory.infrastructure.messaging.dto;

import java.util.UUID;

// 오더 서비스가 발행한 이벤트를 읽어들이기 위한 수신 전용 DTO
public record StockRestoreMessage(
    UUID optionId,
    Integer quantity
) {
}
