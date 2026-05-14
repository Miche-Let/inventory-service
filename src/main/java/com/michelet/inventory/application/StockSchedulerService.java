package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.DailyStockResetEvent;
import com.michelet.inventory.domain.repository.StockRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSchedulerService {

    private final StockRepository stockRepository;
    private final InventoryOutboxHelper outboxHelper;

    @Transactional
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void resetDailyStocks() {
        log.info("일일 재고 초기화 스케줄러 시작...");

        int updatedCount = stockRepository.resetDailyStock();

        log.info("일일 재고 초기화 완료! 업데이트된 상품 옵션 수: {}", updatedCount);

        if (updatedCount > 0) {
            LocalDate todayInSeoul = LocalDate.now(ZoneId.of("Asia/Seoul"));
            DailyStockResetEvent event = new DailyStockResetEvent(todayInSeoul, updatedCount);

            // 현재 트랜잭션의 일부로 Outbox 테이블에 적재됨 (커밋 시 함께 저장)
            outboxHelper.append("STOCK", "ALL_STOCKS", "DAILY_STOCK_RESET", event);
        }
    }
}
