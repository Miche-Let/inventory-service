package com.michelet.inventory.domain.model.vo;

import java.math.BigDecimal;

public record Price(BigDecimal value) {
    public Price {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0원 이상이어야 합니다.");
        }
    }
}
