package com.michelet.inventory.infrastructure.messaging;

import com.michelet.common.exception.BusinessException;
import com.michelet.inventory.application.InventoryOutboxHelper;
import com.michelet.inventory.application.StockLockFacade;
import com.michelet.inventory.infrastructure.messaging.dto.OrderCreatedMessage;
import com.michelet.inventory.infrastructure.messaging.dto.OrderRejectedEvent;
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
    private final InventoryOutboxHelper outboxHelper;

    // 오더 생성 이벤트를 비동기로 받아서 다중 락 처리
    @KafkaListener(
        topics = "${inventory.kafka.topic.order-created:order.created}",
        groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}"
    )
    public void consumeOrderCreated(OrderCreatedMessage payload) {
        log.info("[Kafka Consumer] 신규 주문 생성 메시지 수신 -> 재고 다중 차감 시도: reservationId={}", payload.reservationId());
        try {
            stockLockFacade.reserveOrderStocksWithLock(payload);
        } catch (IllegalArgumentException e) {
            log.error("[Kafka Consumer] 비즈니스/검증 룰 위반 에러 (DLT 직행 대상). reservationId: {}", payload.reservationId(), e);
            throw e;
        } catch (BusinessException e) {
            // 품절, 한도 초과 등 비즈니스 예외 시 오더 서비스에 거절 이벤트 전송
            log.warn("[Kafka Consumer] 비즈니스 로직에 의한 재고 차감 실패 (거절 이벤트 정상 발행). 사유: {}", e.getMessage());
            outboxHelper.append("ORDER", payload.reservationId().toString(), "ORDER_REJECTED",
                new OrderRejectedEvent(payload.reservationId(), e.getMessage()));
        } catch (Exception e) {
            log.error("[Kafka Consumer] 재고 차감 중 일시적 에러 발생 (재시도 대상). reservationId: {}", payload.reservationId(), e);
            throw new RuntimeException("재고 다중 차감 실패", e);
        }
    }

    @KafkaListener(
        topics = "${inventory.kafka.topic.restore-request:order.stock-restore.requested}",
        groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}"
    )
    public void consumeStockRestoreRequest(StockRestoreMessage payload) {
        if (payload == null) {
            log.error("[Kafka Consumer] 잘못된 복구 이벤트 수신: payload가 null입니다 (Tombstone 메시지일 가능성).");
            // IllegalArgumentException을 던지면 아래 catch 블록에서 잡지 않고 밖으로 던져져서 DLT로 직행함
            throw new IllegalArgumentException("재고 복구 이벤트 파싱 오류: payload는 null일 수 없습니다.");
        }

        log.info("[Kafka Consumer] 재고 복구 이벤트 수신: eventId={}, optionId={}, quantity={}", payload.eventId(),
            payload.optionId(), payload.quantity());

        try {
            // 파사드를 통해 분산 락을 걸고 안전하게 재고 복구 로직 실행
            // 전달받은 eventId를 파사드 요청에 포함
            RestoreStockRequest request = new RestoreStockRequest(payload.optionId(), payload.quantity(),
                payload.eventId());
            stockLockFacade.restoreStockWithLock(request);

            log.info("[Kafka Consumer] 재고 복구 로직 처리 완료! eventId: {}, optionId: {}, quantity: {}", payload.eventId(),
                payload.optionId(), payload.quantity());

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
