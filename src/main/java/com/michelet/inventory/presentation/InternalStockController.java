package com.michelet.inventory.presentation;

import com.michelet.common.response.ApiResponse;
import com.michelet.inventory.application.StockCommandService;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/stocks")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockCommandService stockCommandService;

//    //TODO common auth mvc 수정되면 활성화
//    @PostMapping("/reserve")
//    @InternalServiceOnly
//    public ResponseEntity<ApiResponse<Void>> reserveStock(
//        @RequestBody @Valid ReserveStockRequest request
//    ) {
//        stockCommandService.reserveStockWithRetry(request);
//        return ResponseEntity.ok(ApiResponse.ok(null));
//    }

    //FIXME 임시적용 - common webmvc 수정되면 삭제
    @PostMapping("/reserve")
    public ResponseEntity<ApiResponse<Void>> reserveStock(
        @RequestHeader(value = "X-User-Role", required = true) String userRole,
        @RequestBody @Valid ReserveStockRequest request
    ) {
        if (!"SYSTEM".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "시스템 내부 통신만 접근 가능합니다.");
        }

        stockCommandService.reserveStockWithRetry(request);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
