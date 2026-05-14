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
        topics = "${inventory.kafka.topic.restore-request:order.stock-restore.requested}",
        groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}"
    )
    public void consumeStockRestoredEvent(StockRestoreMessage payload) {
        if (payload == null) {
            log.error("[Kafka Consumer] 잘못된 복구 이벤트 수신: payload가 null입니다 (Tombstone 메시지일 가능성).");
            // IllegalArgumentException을 던지면 아래 catch 블록에서 잡지 않고 밖으로 던져져서 DLT로 직행함
            throw new IllegalArgumentException("재고 복구 이벤트 파싱 오류: payload는 null일 수 없습니다.");
        }

        log.info("[Kafka Consumer] 재고 복구 이벤트 수신: optionId={}, quantity={}", payload.optionId(), payload.quantity());

        try {
            // 파사드를 통해 분산 락을 걸고 안전하게 재고 복구 로직 실행
            // TODO(`#26`): reservationId를 Kafka 페이로드에 포함시켜 멱등성 키로 활용
            RestoreStockRequest request = new RestoreStockRequest(payload.optionId(), payload.quantity(), null);
            stockLockFacade.restoreStockWithLock(request);

            log.info("[Kafka Consumer] 재고 복구 완료! optionId: {}, quantity: {}", payload.optionId(), payload.quantity());

        } catch (IllegalArgumentException e) {
            // 데이터 검증 실패나 비즈니스 룰 위반 시 -> 즉각 DLT로 직행하도록 원본 예외 던짐
            log.error("[Kafka Consumer] 비즈니스/검증 룰 위반 에러 (DLT 직행 대상). optionId: {}", payload.optionId(), e);
            throw e;
        } catch (Exception e) {
            // 락 대기 시간 초과 등 일시적 장애 -> 재시도(Retry)를 위해 RuntimeException으로 래핑
            log.error("[Kafka Consumer] 재고 복구 이벤트 처리 중 일시적 에러 발생 (재시도 대상). optionId: {}", payload.optionId(), e);
            throw new RuntimeException("재고 복구 컨슈머 처리 실패", e);
        }
    }
}
