package com.michelet.inventory.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.inventory.application.StockCommandService;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import com.michelet.inventory.presentation.dto.RestoreStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/stocks")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockCommandService stockCommandService;

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<Void>> reserveStock(
        @RequestBody @Valid ReserveStockRequest request
    ) {
        stockCommandService.reserveStockWithRetry(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<Void>> restoreStock(
        @RequestBody @Valid RestoreStockRequest request
    ) {
        stockCommandService.restoreStockWithRetry(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
