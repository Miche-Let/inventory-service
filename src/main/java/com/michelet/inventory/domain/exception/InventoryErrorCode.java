package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode {

    OUT_OF_STOCK(409, "INVENTORY_001", "일일 판매 가능 수량을 초과했습니다."),
    SOLD_OUT(409, "INVENTORY_002", "전체 재고가 모두 소진되었습니다."),
    MAX_LIMIT_EXCEEDED(400, "INVENTORY_003", "1인당 최대 구매 가능 수량을 초과했습니다.");

    private final int httpStatus;
    private final String code;
    private final String message;
}
