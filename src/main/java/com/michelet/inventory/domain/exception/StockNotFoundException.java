package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;

public class StockNotFoundException extends BusinessException {

    public StockNotFoundException() {
        super(InventoryErrorCode.STOCK_NOT_FOUND);
    }
}
