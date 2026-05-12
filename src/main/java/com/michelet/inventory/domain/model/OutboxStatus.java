package com.michelet.inventory.domain.model;

public enum OutboxStatus {
    INIT,       // 발행 대기
    PUBLISHED   // 발행 완료
}
