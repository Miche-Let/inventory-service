package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.DailyStockResetEvent;
import com.michelet.inventory.domain.repository.StockRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSchedulerService {

    private final StockRepository stockRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${inventory.kafka.topic.daily-reset:stock.daily-reset}")
    private String topicDailyReset;

    // "초 분 시 일 월 요일" 순서임
    // 매일 자정(0시 0분 0초)에 실행하려면: @Scheduled(cron = "0 0 0 * * *")
    // (테스트용) 1분마다 실행하려면: @Scheduled(cron = "0 * * * * *")

    @Transactional
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul") // 한국 시각 기준 매일 자정
    public void resetDailyStocks() {
        log.info("일일 재고 초기화 스케줄러 시작...");

        int updatedCount = stockRepository.resetDailyStock();

        log.info("일일 재고 초기화 완료! 업데이트된 상품 옵션 수: {}", updatedCount);

        // 카탈로그 서비스(MongoDB)에도 이 초기화 사실을 알려야해서 - 여기서 전체 재고 초기화 이벤트를 Kafka로 던져줌
        if (updatedCount > 0) {
            LocalDate todayInSeoul = LocalDate.now(ZoneId.of("Asia/Seoul"));
            DailyStockResetEvent event = new DailyStockResetEvent(todayInSeoul, updatedCount);

            // DB 트랜잭션이 정상적으로 커밋된 이후에만 발행되도록!
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendKafkaEvent(event);
                    }
                });
            } else {
                sendKafkaEvent(event);
            }
        }
    }

    private void sendKafkaEvent(DailyStockResetEvent event) {
        kafkaTemplate.send(topicDailyReset, "ALL_STOCKS", event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("일일 재고 초기화 카프카 이벤트 발행 실패", ex);
                }
            });
    }
}
