package com.michelet.inventory.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * 서비스 계층의 실행 결과를 담는 DTO
 */
public record ProductResult(
    UUID productId,
    List<OptionResult> options
) {
    public record OptionResult(UUID optionId, String name) {
    }
}
