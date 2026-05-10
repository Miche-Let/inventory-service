package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode {

    OUT_OF_STOCK(409, "INVENTORY_001", "일일 판매 가능 수량을 초과했습니다."),
    SOLD_OUT(409, "INVENTORY_002", "전체 재고가 모두 소진되었습니다."),
    MAX_LIMIT_EXCEEDED(400, "INVENTORY_003", "1인당 최대 구매 가능 수량을 초과했습니다."),
    STOCK_NOT_FOUND(404, "INVENTORY_004", "해당 재고 정보를 찾을 수 없습니다."),
    CONCURRENCY_ERROR(429, "INVENTORY_005", "현재 주문 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    PRODUCT_NOT_MODIFIABLE(400, "INVENTORY_006", "만료되거나 삭제된 상품은 수정할 수 없습니다."),
    INVALID_STATUS_TRANSITION(400, "INVENTORY_007", "허용되지 않는 상태 전이입니다.");

    private final int httpStatus;
    private final String code;
    private final String message;
}
