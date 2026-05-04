package com.michelet.inventory.presentation;

import com.michelet.inventory.application.ExhibitionSchedulerService;
import com.michelet.inventory.application.StockSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/scheduler")
@RequiredArgsConstructor
public class SchedulerTriggerController {

    private final StockSchedulerService stockSchedulerService;
    private final ExhibitionSchedulerService exhibitionSchedulerService;

    // 일일 재고 강제 리셋 버튼 - 테스트용!
    @PostMapping("/trigger-stock")
    public String triggerStock(
        @RequestHeader(value = "X-User-Role", required = true) String userRole
    ) {
        if (!"SYSTEM".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "시스템 내부 통신만 접근 가능합니다.");
        }
        stockSchedulerService.resetDailyStocks();
        return "재고 리셋 트리거 작동";
    }

    // 정시 전시 상태 강제 갱신 버튼 - 테스트용!
    @PostMapping("/trigger-exhibition")
    public String triggerExhibition(
        @RequestHeader(value = "X-User-Role", required = true) String userRole
    ) {
        if (!"SYSTEM".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "시스템 내부 통신만 접근 가능합니다.");
        }
        exhibitionSchedulerService.updateExhibitionStatus();
        return "전시 상태 갱신 트리거 작동";
    }
}
