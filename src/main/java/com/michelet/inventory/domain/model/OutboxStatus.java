package com.michelet.inventory.domain.model;

public enum OutboxStatus {
    INIT,       // 발행 대기
    PUBLISHED,  // 발행 완료
    FAILED      // 최대 재시도 초과로 영구 실패 (격리)
}
