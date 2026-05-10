package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;
import com.michelet.common.exception.ErrorCode;
import com.michelet.inventory.domain.model.ProductStatus;

public class ProductNotModifiableException extends BusinessException {

    public ProductNotModifiableException(ProductStatus status) {
        super(new ErrorCode() {
            @Override
            public String getCode() {
                return InventoryErrorCode.PRODUCT_NOT_MODIFIABLE.getCode();
            }

            @Override
            public String getMessage() {
                return "만료되거나 삭제된 상품은 수정할 수 없습니다. (현재 상태: " + status + ")";
            }

            @Override
            public int getHttpStatus() {
                return InventoryErrorCode.PRODUCT_NOT_MODIFIABLE.getHttpStatus();
            }
        });
    }
}
