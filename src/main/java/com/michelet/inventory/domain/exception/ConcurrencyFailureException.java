package com.michelet.inventory.domain.exception;

import com.michelet.common.exception.BusinessException;

public class ConcurrencyFailureException extends BusinessException {
    public ConcurrencyFailureException() {
        super(InventoryErrorCode.CONCURRENCY_ERROR);
    }
}
