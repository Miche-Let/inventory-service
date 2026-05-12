package com.michelet.inventory.infrastructure.messaging;

import com.michelet.inventory.application.StockLockFacade;
import com.michelet.inventory.infrastructure.messaging.dto.StockRestoreMessage;
import com.michelet.inventory.presentation.dto.RestoreStockRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final StockLockFacade stockLockFacade;

    @KafkaListener(
        topics = "${inventory.kafka.topic.restored:stock.restored}",
        groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}"
    )
    public void consumeStockRestoredEvent(StockRestoreMessage payload) {
        log.info("[Kafka Consumer] 재고 복구 이벤트 수신: optionId={}, quantity={}", payload.optionId(), payload.quantity());

        try {
            // 파사드를 통해 분산 락을 걸고 안전하게 재고 복구 로직 실행
            // TODO(`#26`): reservationId를 Kafka 페이로드에 포함시켜 멱등성 키로 활용
            RestoreStockRequest request = new RestoreStockRequest(payload.optionId(), payload.quantity(), null);
            stockLockFacade.restoreStockWithLock(request);

            log.info("[Kafka Consumer] 재고 복구 완료! optionId: {}, quantity: {}", payload.optionId(), payload.quantity());

        } catch (IllegalArgumentException e) {
            // 데이터 형식이 잘못된 경우 무의미한 재시도를 막고 즉시 DLT로 보내기 위해 원래 에러를 던짐
            log.error("[Kafka Consumer] 비즈니스/검증 룰 위반 에러 (DLT 직행 대상). optionId: {}", payload.optionId(), e);
            throw e;
        } catch (Exception e) {
            // DB 락 타임아웃 등 일시적인 장애는 DefaultErrorHandler가 재시도할 수 있도록 래핑하여 던짐
            log.error("[Kafka Consumer] 재고 복구 이벤트 처리 중 일시적 에러 발생 (재시도 대상). optionId: {}", payload.optionId(), e);
            throw new RuntimeException("재고 복구 컨슈머 처리 실패", e);
        }
    }
}
