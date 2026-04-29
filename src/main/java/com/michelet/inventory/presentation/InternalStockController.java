package com.michelet.inventory.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.inventory.application.StockCommandService;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/stocks")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockCommandService stockCommandService;

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<Void>> reserveStock(
        @RequestHeader(value = "X-User-Role", required = false) String userRole,
        @RequestBody @Valid ReserveStockRequest request
    ) {
        if (!"SYSTEM".equals(userRole)) {
            throw new IllegalArgumentException("시스템 내부 통신만 접근 가능합니다.");
        }

        stockCommandService.reserveStockWithRetry(request);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
