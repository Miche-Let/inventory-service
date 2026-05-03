package com.michelet.inventory.presentation;

import com.michelet.inventory.application.ExhibitionSchedulerService;
import com.michelet.inventory.application.StockSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/scheduler")
@RequiredArgsConstructor
public class SchedulerTriggerController {

    private final StockSchedulerService stockSchedulerService;
    private final ExhibitionSchedulerService exhibitionSchedulerService;

    // 일일 재고 강제 리셋 버튼 - 테스트용!
    @PostMapping("/trigger-stock")
    public String triggerStock() {
        stockSchedulerService.resetDailyStocks();
        return "OK - Stock Reset Triggered";
    }

    // 정시 전시 상태 강제 갱신 버튼 - 테스트용!
    @PostMapping("/trigger-exhibition")
    public String triggerExhibition() {
        exhibitionSchedulerService.updateExhibitionStatus();
        return "OK - Exhibition Status Triggered";
    }
}
