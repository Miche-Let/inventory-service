package com.michelet.inventory.application.dto;

import java.util.UUID;

public record StockRestoredEvent(
    UUID optionId,
    int totalQuantity,
    int currentDailyStock
) {
}
