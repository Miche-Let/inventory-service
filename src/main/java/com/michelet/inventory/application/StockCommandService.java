package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.application.dto.StockRestoredEvent;
import com.michelet.inventory.domain.exception.ConcurrencyFailureException;
import com.michelet.inventory.domain.exception.StockNotFoundException;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import com.michelet.inventory.presentation.dto.RestoreStockRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${inventory.retry.max-count:3}")
    private int maxRetryCount;

    @Value("${inventory.kafka.topic.reserved:stock.reserved}")
    private String topicStockReserved;

    @Value("${inventory.kafka.topic.restored:stock.restored}")
    private String topicStockRestored;

    // 재시도 횟수 설정 오류 방어 - 최소 1회 실행 보장
    @PostConstruct
    public void validateConfig() {
        if (this.maxRetryCount <= 0) {
            log.warn("maxRetryCount [{}] 설정이 0 이하입니다. 1로 강제 조정합니다.", this.maxRetryCount);
            this.maxRetryCount = Math.max(1, this.maxRetryCount);
        }
    }

    // @Transactional을 제거 - AOP Self-Invocation 방지
    public void reserveStockWithRetry(ReserveStockRequest request) {
        int retryCount = 0;
        while (retryCount < maxRetryCount) {
            try {
                // 1. 매 재시도마다 독립된 새로운 트랜잭션을 연다
                StockReservedEvent event = transactionTemplate.execute(status -> reserveStockInternal(request));

                // 2. TransactionTemplate이 에러 없이 끝나면 DB 커밋이 완료된 것
                // Fire-and-forget 방지. whenComplete 콜백을 달아 전송 실패 시 인지 및 후속 처리 가능하도록 수정
                kafkaTemplate.send(topicStockReserved, request.optionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 메시지 발행 실패 (데이터 불일치 위험)! optionId: {}", request.optionId(), ex);
                            // TODO 추후 재처리 로직이나 아웃박스 패턴으로 고도화 필요
                        } else {
                            long offset =
                                (result != null && result.getRecordMetadata() != null) ? result.getRecordMetadata()
                                    .offset() : -1;
                            log.info("Kafka 예약 메시지 발행 성공! offset: {}", offset);
                        }
                    });
                return;

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("재고 차감 동시성(낙관적 락) 충돌 발생. 재시도 횟수: {}/{}", retryCount, maxRetryCount);
                if (retryCount >= maxRetryCount) {
                    throw new ConcurrencyFailureException();
                }
                backoff("차감");
            }
        }
    }

    // 트랜잭션 내부에서 실행될 순수 비즈니스 로직
    private StockReservedEvent reserveStockInternal(ReserveStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

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

    //복구로직
    public void restoreStockWithRetry(RestoreStockRequest request) {
        int retryCount = 0;
        while (retryCount < maxRetryCount) {
            try {
                // 1. 매 재시도마다 독립된 새로운 트랜잭션을 연다
                StockRestoredEvent event = transactionTemplate.execute(status -> restoreStockInternal(request));

                // 2. DB 커밋 완료 후 Kafka 발행
                kafkaTemplate.send(topicStockRestored, request.optionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 복구 메시지 발행 실패 (재고 유실 위험)! optionId: {}", request.optionId(), ex);
                        } else {
                            long offset =
                                (result != null && result.getRecordMetadata() != null) ? result.getRecordMetadata()
                                    .offset() : -1;
                            log.info("Kafka 복구 메시지 발행 성공! offset: {}", offset);
                        }
                    });
                return;

            } catch (ObjectOptimisticLockingFailureException e) {
                retryCount++;
                log.warn("재고 복구 동시성(낙관적 락) 충돌 발생. 재시도 횟수: {}/{}", retryCount, maxRetryCount);
                if (retryCount >= maxRetryCount) {
                    throw new ConcurrencyFailureException();
                }
                backoff("복구");
            }
        }
    }

    // 트랜잭션 내부에서 실행될 순수 비즈니스 로직
    private StockRestoredEvent restoreStockInternal(RestoreStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

        // 1. 도메인 로직: 재고 복구
        stock.restore(request.quantity());

        // 2. DB 업데이트 (더티 체킹 후 flush)
        stockRepository.save(stock);

        // 3. Kafka 이벤트 발행용 객체 리턴
        return new StockRestoredEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
    }

    private void backoff(String operationType) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재고 " + operationType + " 대기 중 쓰레드가 강제 종료되었습니다.", ie);
        }
    }
}
