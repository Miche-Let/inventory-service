package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;
import com.michelet.common.exception.ErrorCode;
import com.michelet.inventory.domain.model.ProductStatus;

public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException(ProductStatus from, ProductStatus to) {
        super(new ErrorCode() {
            @Override
            public String getCode() {
                return InventoryErrorCode.INVALID_STATUS_TRANSITION.getCode();
            }

            @Override
            public String getMessage() {
                return from + " 상태에서 " + to + " 상태로의 전이는 허용되지 않습니다.";
            }

            @Override
            public int getHttpStatus() {
                return InventoryErrorCode.INVALID_STATUS_TRANSITION.getHttpStatus();
            }
        });
    }
}
