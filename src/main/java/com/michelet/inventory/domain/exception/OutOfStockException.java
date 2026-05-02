package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;

public class OutOfStockException extends BusinessException {
    public OutOfStockException() {
        super(InventoryErrorCode.OUT_OF_STOCK);
    }
}
