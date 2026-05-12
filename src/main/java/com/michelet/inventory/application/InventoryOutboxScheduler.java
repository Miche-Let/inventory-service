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
import com.michelet.inventory.infrastructure.repository.JpaInventoryOutboxRepository;
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

    private final JpaInventoryOutboxRepository outboxRepository;
    private final InventoryOutboxHelper outboxHelper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // JSON 문자열을 객체로 복원하기 위한 매퍼 주입
    private final ObjectMapper objectMapper;

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
                log.info("[Inventory Outbox Scheduler] 이미 처리된 이벤트입니다 (낙관적 락). Outbox ID: {}", event.getId());
            } catch (Exception e) {
                log.error("[Inventory Outbox Scheduler] 이벤트 발행 실패. 다음 주기에 재시도합니다. Outbox ID: {}", event.getId(), e);
            }
        }
    }

    // 이벤트 타입에 따른 발행 토픽 라우팅
    // JSON 문자열을 원래 DTO 클래스로 변환
    private Object deserializePayload(String eventType, String jsonPayload) throws Exception {
        return switch (eventType) {
            case "PRODUCT_CREATED" -> objectMapper.readValue(jsonPayload, ProductCreatedEvent.class);
            case "PRODUCT_UPDATED" -> objectMapper.readValue(jsonPayload, ProductUpdatedEvent.class);
            case "PRODUCT_STATUS_CHANGED" -> objectMapper.readValue(jsonPayload, ProductStatusChangedEvent.class);
            case "STOCK_RESERVED" -> objectMapper.readValue(jsonPayload, StockReservedEvent.class);
            case "STOCK_RESTORED" -> objectMapper.readValue(jsonPayload, StockRestoredEvent.class);
            case "DAILY_STOCK_RESET" -> objectMapper.readValue(jsonPayload, DailyStockResetEvent.class);
            // 매핑 안 된 이벤트는 그냥 String으로 보냄
            default -> jsonPayload;
        };
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "PRODUCT_CREATED" -> topicProductCreated;
            case "PRODUCT_UPDATED" -> topicProductUpdated;
            case "PRODUCT_STATUS_CHANGED" -> topicStatusChanged;
            case "STOCK_RESERVED" -> topicStockReserved;
            case "STOCK_RESTORED" -> topicStockRestored;
            case "DAILY_STOCK_RESET" -> topicDailyReset;
            default -> "inventory.unknown.event";
        };
    }
}
