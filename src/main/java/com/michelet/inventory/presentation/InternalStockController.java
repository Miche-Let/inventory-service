package com.michelet.inventory.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.inventory.application.StockLockFacade;
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

    private final StockLockFacade stockLockFacade;

    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<Void>> reserveStock(
        @RequestBody @Valid ReserveStockRequest request
    ) {
        stockLockFacade.reserveStockWithLock(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Kafka 비동기 통신(order.stock-restore.requested)으로 대체됨. 향후 삭제 예정
     */
    @Deprecated(since = "1.0", forRemoval = true)
    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<Void>> restoreStock(
        @RequestBody @Valid RestoreStockRequest request
    ) {
        stockLockFacade.restoreStockWithLock(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
