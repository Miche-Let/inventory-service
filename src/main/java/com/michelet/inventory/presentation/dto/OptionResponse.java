package com.michelet.inventory.presentation.dto;

import java.util.UUID;

/**
 * 응답에 포함될 옵션 정보
 */
record OptionResponse(
    UUID optionId,
    String name
) {
}
