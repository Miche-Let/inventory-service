package com.michelet.inventory.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    ACTIVE("판매 중"),
    HIDDEN("숨김"),
    DELETED("삭제"),
    SOLDOUT("품절");

    private final String description;
}
