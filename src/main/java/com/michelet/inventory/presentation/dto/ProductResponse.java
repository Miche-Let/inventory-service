package com.michelet.inventory.presentation.dto;

import com.michelet.inventory.application.dto.ProductResult;
import java.util.List;
import java.util.UUID;

/**
 * API 응답으로 나가는 최종 DTO
 */
public record ProductResponse(
    UUID productId,
    List<OptionResponse> options
) {
    public static ProductResponse from(ProductResult result) {
        List<OptionResponse> optionResponses = result.options().stream()
            .map(opt -> new OptionResponse(opt.optionId(), opt.name()))
            .toList();

        return new ProductResponse(result.productId(), optionResponses);
    }
}
