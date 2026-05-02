package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;

public class SoldOutException extends BusinessException {

    public SoldOutException() {
        super(InventoryErrorCode.SOLD_OUT);
    }
}
