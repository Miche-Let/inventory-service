package com.michelet.inventory.application.dto;

import java.util.UUID;

/**
 * 서비스 계층의 실행 결과를 담는 DTO
 */
public record ProductResult(UUID productId) {
}
