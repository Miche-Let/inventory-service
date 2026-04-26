package com.michelet.inventory.domain.model.vo;

public record Quantity(Integer value) {
    public Quantity {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("수량은 0개 이상이어야 합니다.");
        }
    }
}
