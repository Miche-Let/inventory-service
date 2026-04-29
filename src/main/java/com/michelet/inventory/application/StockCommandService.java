package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRY_COUNT = 3;
    private static final String TOPIC_STOCK_RESERVED = "stock.reserved";

    @Transactional
    public void reserveStockWithRetry(ReserveStockRequest request) {
        int retryCount = 0;
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                reserveStock(request);
                return; // 성공 시 종료
            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("재고 차감 동시성 충돌 발생. 재시도 횟수: {}/{}", retryCount, MAX_RETRY_COUNT);
                if (retryCount >= MAX_RETRY_COUNT) {
                    throw new IllegalStateException("주문량이 많아 재고 처리에 실패했습니다. 다시 시도해주세요.");
                }
                try {
                    Thread.sleep(50); // 잠시 대기 후 재시도
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void reserveStock(ReserveStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고 옵션입니다."));

        // 1. 도메인 로직을 통한 3중 검증 및 차감
        stock.reserve(request.quantity());

        // 2. 가독성 위해... 어차피 더티 체킹에 의해 flush 시점에 버전 체크가 발생함
        stockRepository.save(stock);

        // 3. Kafka 이벤트 발행
        StockReservedEvent event = StockReservedEvent.from(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
        kafkaTemplate.send(TOPIC_STOCK_RESERVED, stock.getOptionId().toString(), event);
    }
}
