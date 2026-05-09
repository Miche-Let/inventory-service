package com.michelet.inventory.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    ACTIVE("판매 중"),
    HIDDEN("숨김"),
    SOLDOUT("품절"),
    EXPIRED("전시 만료"),
    DELETED("삭제됨");

    private final String description;

    /**
     * 상품 상태 전이 가능 여부를 검증
     */
    public boolean canTransitionTo(ProductStatus nextStatus) {
        if (this == nextStatus) {
            return true; // 동일 상태로의 변경은 허용 (멱등성 보장)
        }
        if (this == DELETED) {
            return false; // DELETED(삭제)는 완전한 종료 상태이므로 어떤 상태로도 돌아갈 수 없음
        }
        if (this == EXPIRED) {
            // EXPIRED(만료) 상태에서는 오직 삭제(DELETED) 처리만 가능
            return nextStatus == DELETED;
        }
        // ACTIVE, HIDDEN, SOLDOUT 상태는 서로 자유롭게 변경 가능. EXPIRED나 DELETED로도 전이 가능
        return true;
    }
}
