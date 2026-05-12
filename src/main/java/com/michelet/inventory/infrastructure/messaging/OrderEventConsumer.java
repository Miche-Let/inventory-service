package com.michelet.inventory.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${inventory.kafka.topic.restored:stock.restored}",
        groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}"
    )
    public void consumeStockRestoredEvent(String message) {
        log.info("[Kafka Consumer] 재고 복구 이벤트 수신: {}", message);

        try {
            // 1. DTO를 사용하여 JSON 파싱
            StockRestoreMessage payload = objectMapper.readValue(message, StockRestoreMessage.class);

            // 2. 파사드를 통해 분산 락을 걸고 안전하게 재고 복구 로직 실행
            RestoreStockRequest request = new RestoreStockRequest(payload.optionId(), payload.quantity(), null);
            stockLockFacade.restoreStockWithLock(request);

            log.info("[Kafka Consumer] 재고 복구 완료! optionId: {}, quantity: {}", payload.optionId(), payload.quantity());

        } catch (Exception e) {
            log.error("[Kafka Consumer] 재고 복구 이벤트 처리 중 에러 발생! 메시지: {}", message, e);
            throw new RuntimeException("재고 복구 컨슈머 처리 실패", e);
        }
    }
}
