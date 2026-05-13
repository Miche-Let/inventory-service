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
import java.util.List;
import java.util.concurrent.TimeUnit;
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

                // 2. 카프카 전송 및 동기식 대기 (트랜잭션 밖에서 실행됨)
                // 복원된 객체를 보내야 JsonSerializer가 __TypeId__를 세팅함
                kafkaTemplate.send(topic, event.getAggregateId(), originalEventObject)
                    .get(3, TimeUnit.SECONDS);

                outboxHelper.markAsPublished(event.getId());
                log.info("[Inventory Outbox Scheduler] 이벤트 발행 성공! Outbox ID: {}", event.getId());

            } catch (ObjectOptimisticLockingFailureException oole) {
                // 동시성 제어 방어 로그
                log.info("[Inventory Outbox Scheduler] 낙관적 락 충돌 방어 성공 (동시성 경합 혹은 중복 처리 방지). Outbox ID: {}",
                    event.getId());
            } catch (Exception e) {
                log.error("[Inventory Outbox Scheduler] 이벤트 발행 실패. 다음 주기에 재시도합니다. Outbox ID: {}", event.getId(), e);
            }
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
            default -> "inventory.unknown.event";
        };
    }
}
