package com.michelet.inventory.application.dto;

import java.time.LocalDate;

public record DailyStockResetEvent(
    LocalDate resetDate,
    int updatedOptionCount
) {
}
