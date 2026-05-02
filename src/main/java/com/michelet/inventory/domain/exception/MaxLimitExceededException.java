package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;
import com.michelet.common.exception.ErrorCode;

public class MaxLimitExceededException extends BusinessException {

    public MaxLimitExceededException(int maxLimit) {
        // 동적 메시지를 위해 즉석에서 ErrorCode 인터페이스를 구현한 익명 객체를 넘김
        super(new ErrorCode() {
            @Override
            public String getCode() {
                return InventoryErrorCode.MAX_LIMIT_EXCEEDED.getCode();
            }

            @Override
            public String getMessage() {
                return "최대 " + maxLimit + "개까지만 구매 가능합니다."; // 동적 메시지 삽입!
            }

            @Override
            public int getHttpStatus() {
                return InventoryErrorCode.MAX_LIMIT_EXCEEDED.getHttpStatus();
            }
        });
    }
}
