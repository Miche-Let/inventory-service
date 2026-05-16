package com.michelet.inventory.infrastructure.messaging;

import com.michelet.common.exception.BusinessException;
import com.michelet.inventory.application.InventoryOutboxHelper;
import com.michelet.inventory.application.StockLockFacade;
import com.michelet.inventory.application.dto.RestoreStockRequest;
import com.michelet.inventory.domain.exception.InventoryErrorCode;
import com.michelet.inventory.infrastructure.messaging.dto.OrderCreatedMessage;
import com.michelet.inventory.infrastructure.messaging.dto.OrderRejectedEvent;
import com.michelet.inventory.infrastructure.messaging.dto.StockRestoreMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
// 클래스 레벨에서 단일 토픽(inventory.command)을 구독하도록 통합
@KafkaListener(
    topics = "${inventory.kafka.topic.inventory-command:inventory.command}",
    groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}"
)
public class OrderEventConsumer {

    private final StockLockFacade stockLockFacade;
    private final InventoryOutboxHelper outboxHelper;

    // TypeId 헤더를 보고 스프링 카프카가 이 메서드로 라우팅해 줌
    @KafkaHandler
    public void consumeOrderCreated(OrderCreatedMessage payload) {
        // payload null 체크 (Tombstone 메시지 방어 - NPE 방지)
        if (payload == null) {
            log.error("[Kafka Consumer] 잘못된 주문 생성 이벤트 수신: payload가 null입니다 (Tombstone 메시지일 가능성).");
            throw new IllegalArgumentException("주문 생성 이벤트 파싱 오류: payload는 null일 수 없습니다.");
        }

        // null 체크 이후에 로깅 (In-Order 처리)
        log.info("[Kafka Consumer] 신규 주문 생성 메시지 수신 -> 재고 다중 차감 시도 (In-Order 처리): reservationId={}",
            payload.reservationId());
        try {
            stockLockFacade.reserveOrderStocksWithLock(payload);
        } catch (IllegalArgumentException e) {
            log.error("[Kafka Consumer] 비즈니스/검증 룰 위반 에러 (DLT 직행 대상). reservationId: {}", payload.reservationId(), e);
            throw e;
        } catch (BusinessException e) {
            // 락 획득 실패(일시적 경합)인 경우, 주문 거절을 하지 않고 RuntimeException을 던져 카프카 재시도를 유도
            if (InventoryErrorCode.CONCURRENCY_ERROR.name().equals(e.getErrorCode())) {
                log.error("[Kafka Consumer] 락 경합으로 인한 일시적 실패 (재시도 대상). reservationId={}", payload.reservationId(), e);
                throw new RuntimeException("재고 락 경합으로 인한 주문 처리 지연", e);
            }

            // 품절, 한도 초과 등 명확한 비즈니스 예외 시에만 오더 서비스에 거절 이벤트 전송
            log.warn("[Kafka Consumer] 비즈니스 로직에 의한 재고 차감 실패 (거절 이벤트 정상 발행). 사유: {}", e.getMessage());
            // 롤백된 트랜잭션 밖에서 아웃박스를 안전하게 저장하기 위해 appendIndependent 사용!
            outboxHelper.appendIndependent("ORDER", payload.reservationId().toString(), "ORDER_REJECTED",
                new OrderRejectedEvent(payload.reservationId(), e.getMessage()));

        } catch (Exception e) {
            log.error("[Kafka Consumer] 재고 차감 중 일시적 에러 발생 (재시도 대상). reservationId: {}", payload.reservationId(), e);
            throw new RuntimeException("재고 다중 차감 실패", e);
        }
    }

    // 복구 메시지가 들어오면 이 메서드로 라우팅해 줌
    @KafkaHandler
    public void consumeStockRestoreRequest(StockRestoreMessage payload) {
        if (payload == null) {
            log.error("[Kafka Consumer] 잘못된 복구 이벤트 수신: payload가 null입니다 (Tombstone 메시지일 가능성).");
            // IllegalArgumentException을 던지면 아래 catch 블록에서 잡지 않고 밖으로 던져져서 DLT로 직행함
            throw new IllegalArgumentException("재고 복구 이벤트 파싱 오류: payload는 null일 수 없습니다.");
        }

        log.info("[Kafka Consumer] 재고 복구 이벤트 수신 (In-Order 처리): eventId={}, optionId={}, quantity={}", payload.eventId(),
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

    // 만약 예상치 못한 다른 객체가 inventory.command로 들어올 경우, 서버가 터지지 않고 로그만 남기고 무시하도록 방어
    @KafkaHandler(isDefault = true)
    public void unknown(Object object) {
        log.warn("[Kafka Consumer] 알 수 없는 타입의 커맨드가 inventory.command 토픽으로 들어왔습니다: {}", object);
    }
}
