package com.michelet.inventory.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.inventory.application.dto.DailyStockResetEvent;
import com.michelet.inventory.application.dto.ProductCreatedEvent;
import com.michelet.inventory.application.dto.ProductStatusChangedEvent;
import com.michelet.inventory.application.dto.ProductUpdatedEvent;
import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.application.dto.StockRestoredEvent;
import com.michelet.inventory.domain.model.InventoryOutbox;
import com.michelet.inventory.domain.model.OutboxStatus;
import com.michelet.inventory.domain.repository.InventoryOutboxRepository;
import com.michelet.inventory.infrastructure.messaging.dto.OrderApprovedEvent;
import com.michelet.inventory.infrastructure.messaging.dto.OrderRejectedEvent;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryOutboxScheduler {

    private final InventoryOutboxRepository outboxRepository;
    private final InventoryOutboxHelper outboxHelper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // JSON 문자열을 객체로 복원하기 위한 매퍼 주입
    private final ObjectMapper objectMapper;

    // 문자열 상수 추출
    private static final String EVENT_PRODUCT_CREATED = "PRODUCT_CREATED";
    private static final String EVENT_PRODUCT_UPDATED = "PRODUCT_UPDATED";
    private static final String EVENT_STATUS_CHANGED = "PRODUCT_STATUS_CHANGED";
    private static final String EVENT_STOCK_RESERVED = "STOCK_RESERVED";
    private static final String EVENT_STOCK_RESTORED = "STOCK_RESTORED";
    private static final String EVENT_DAILY_RESET = "DAILY_STOCK_RESET";
    private static final String EVENT_ORDER_APPROVED = "ORDER_APPROVED";
    private static final String EVENT_ORDER_REJECTED = "ORDER_REJECTED";

    @Value("${inventory.kafka.topic.product-created:product.created}")
    private String topicProductCreated;
    @Value("${inventory.kafka.topic.product-updated:product.updated}")
    private String topicProductUpdated;
    @Value("${inventory.kafka.topic.status-changed:product.status-changed}")
    private String topicStatusChanged;
    @Value("${inventory.kafka.topic.reserved:stock.reserved}")
    private String topicStockReserved;
    @Value("${inventory.kafka.topic.restored:stock.restored}")
    private String topicStockRestored;
    @Value("${inventory.kafka.topic.daily-reset:stock.daily-reset}")
    private String topicDailyReset;
    @Value("${inventory.kafka.topic.order-approved:order.approved}")
    private String topicOrderApproved;
    @Value("${inventory.kafka.topic.order-rejected:order.rejected}")
    private String topicOrderRejected;

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        // 1. OOM 방지 및 순서 보장을 위해 Top N 배치 조회
        List<InventoryOutbox> pendingEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.INIT);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[Inventory Outbox Scheduler] {}개의 미발행 이벤트를 찾아 Kafka 전송을 시도합니다.", pendingEvents.size());

        for (InventoryOutbox event : pendingEvents) {
            try {
                String topic = resolveTopic(event.getEventType());

                // String(JSON)을 다시 원본 Event 객체로 복원
                Object originalEventObject = deserializePayload(event.getEventType(), event.getPayload());

                // 블로킹(.get) 제거 -> 비동기 발송 콜백(.whenComplete) 적용
                kafkaTemplate.send(topic, event.getAggregateId(), originalEventObject)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            try {
                                outboxHelper.markAsPublished(event.getId());
                                log.info("[Inventory Outbox Scheduler] 이벤트 발행 성공! Outbox ID: {}", event.getId());
                            } catch (ObjectOptimisticLockingFailureException oole) {
                                log.info("[Inventory Outbox Scheduler] 낙관적 락 방어 (동시성 경합). Outbox ID: {}",
                                    event.getId());
                            } catch (Exception updateEx) {
                                log.error("[Inventory Outbox Scheduler] DB 상태 업데이트 실패. Outbox ID: {}", event.getId(),
                                    updateEx);
                            }
                        } else {
                            log.error("[Inventory Outbox Scheduler] 카프카 이벤트 발행 실패. Outbox ID: {}", event.getId(), ex);
                            safeHandleFailure(event.getId());
                        }
                    });

            } catch (Exception e) {
                // 역직렬화 실패, 토픽 변환 실패 등 무한 에러 유발 시
                log.error("[Inventory Outbox Scheduler] 이벤트 전송 준비 중 예외 발생. Outbox ID: {}", event.getId(), e);
                safeHandleFailure(event.getId());
            }
        }
    }

    // 재시도 횟수 처리 및 상태 변경을 돕는 실패 처리 메서드
    private void safeHandleFailure(UUID eventId) {
        try {
            outboxHelper.handleFailure(eventId);
        } catch (ObjectOptimisticLockingFailureException oole) {
            log.info("[Inventory Outbox Scheduler] 실패 마킹 중 낙관적 락 방어. Outbox ID: {}", eventId);
        } catch (Exception e) {
            log.error("[Inventory Outbox Scheduler] 실패 상태 업데이트 중 예외 발생. Outbox ID: {}", eventId, e);
        }
    }

    // 이벤트 타입에 따른 발행 토픽 라우팅
    // JSON 문자열을 원래 DTO 클래스로 변환
    private Object deserializePayload(String eventType, String jsonPayload) throws Exception {
        return switch (eventType) {
            case EVENT_PRODUCT_CREATED -> objectMapper.readValue(jsonPayload, ProductCreatedEvent.class);
            case EVENT_PRODUCT_UPDATED -> objectMapper.readValue(jsonPayload, ProductUpdatedEvent.class);
            case EVENT_STATUS_CHANGED -> objectMapper.readValue(jsonPayload, ProductStatusChangedEvent.class);
            case EVENT_STOCK_RESERVED -> objectMapper.readValue(jsonPayload, StockReservedEvent.class);
            case EVENT_STOCK_RESTORED -> objectMapper.readValue(jsonPayload, StockRestoredEvent.class);
            case EVENT_DAILY_RESET -> objectMapper.readValue(jsonPayload, DailyStockResetEvent.class);
            case EVENT_ORDER_APPROVED -> objectMapper.readValue(jsonPayload, OrderApprovedEvent.class);
            case EVENT_ORDER_REJECTED -> objectMapper.readValue(jsonPayload, OrderRejectedEvent.class);
            // 매핑 안 된 이벤트를 String으로 보내면 직렬화 에러 발생! 예외를 던져서 스케줄러 재시도 루프로 넘김
            default -> {
                log.warn("등록되지 않은 알 수 없는 이벤트 타입입니다: {}", eventType);
                throw new IllegalArgumentException("Unknown event type: " + eventType);
            }
        };
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case EVENT_PRODUCT_CREATED -> topicProductCreated;
            case EVENT_PRODUCT_UPDATED -> topicProductUpdated;
            case EVENT_STATUS_CHANGED -> topicStatusChanged;
            case EVENT_STOCK_RESERVED -> topicStockReserved;
            case EVENT_STOCK_RESTORED -> topicStockRestored;
            case EVENT_DAILY_RESET -> topicDailyReset;
            case EVENT_ORDER_APPROVED -> topicOrderApproved;
            case EVENT_ORDER_REJECTED -> topicOrderRejected;
            default -> "inventory.unknown.event";
        };
    }
}
