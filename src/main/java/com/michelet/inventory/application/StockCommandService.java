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
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate; // 트랜잭션을 수동으로 제어하기 위해 주입

    private static final int MAX_RETRY_COUNT = 3;
    private static final String TOPIC_STOCK_RESERVED = "stock.reserved";

    // @Transactional을 제거 - AOP Self-Invocation 방지
    public void reserveStockWithRetry(ReserveStockRequest request) {
        int retryCount = 0;
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                // 1. 매 재시도마다 독립된 새로운 트랜잭션을 연다
                StockReservedEvent event = transactionTemplate.execute(status -> reserveStockInternal(request));

                // 2. TransactionTemplate이 에러 없이 끝나면 DB 커밋이 완료된 것
                // Fire-and-forget 방지. whenComplete 콜백을 달아 전송 실패 시 인지 및 후속 처리 가능하도록 수정
                kafkaTemplate.send(TOPIC_STOCK_RESERVED, request.optionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 메시지 발행 실패 (데이터 불일치 위험)! optionId: {}", request.optionId(), ex);
                            // 추후 재처리 로직이나 아웃박스 패턴으로 고도화 필요
                        } else {
                            long offset =
                                (result != null && result.getRecordMetadata() != null) ? result.getRecordMetadata()
                                    .offset() : -1;
                            log.info("Kafka 메시지 발행 성공! offset: {}", offset);
                        }
                    });
                return;

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("재고 차감 동시성(낙관적 락) 충돌 발생. 재시도 횟수: {}/{}", retryCount, MAX_RETRY_COUNT);
                if (retryCount >= MAX_RETRY_COUNT) {
                    throw new IllegalStateException("주문량이 많아 재고 처리에 실패했습니다. 다시 시도해주세요.");
                }
                try {
                    Thread.sleep(50); // 잠시 대기 후 재시도
                } catch (InterruptedException ie) {
                    // 쓰레드 인터럽트 발생 시 플래그를 세우고 루프를 탈출하여 예외를 던지도록 수정
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("재고 처리 대기 중 쓰레드가 강제 종료되었습니다.", ie);
                }
            }
        }
    }

    // 트랜잭션 내부에서 실행될 순수 비즈니스 로직
    private StockReservedEvent reserveStockInternal(ReserveStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고 옵션입니다."));

        // 1. 도메인 로직을 통한 3중 검증 및 차감
        stock.reserve(request.quantity());

        // 2. 가독성 위해... 어차피 더티 체킹에 의해 flush 시점에 버전 체크가 발생함
        stockRepository.save(stock);

        // 3. Kafka 이벤트 발행
        return new StockReservedEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
    }
}
