package com.michelet.inventory.application.dto;

import java.util.UUID;

public record StockReservedEvent(
    UUID optionId,
    Integer totalQuantity,
    Integer currentDailyStock
) {
    public static StockReservedEvent from(UUID optionId, Integer totalQuantity, Integer currentDailyStock) {
        return new StockReservedEvent(optionId, totalQuantity, currentDailyStock);
    }
}
