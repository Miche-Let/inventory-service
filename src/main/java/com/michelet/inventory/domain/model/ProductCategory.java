package com.michelet.inventory.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductCategory {
    WINE("와인"),
    MEALKIT("밀키트"),
    GOODS("굿즈"),
    EVENT("이벤트");

    private final String description;
}
