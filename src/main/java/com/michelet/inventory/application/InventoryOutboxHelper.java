package com.michelet.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.inventory.domain.model.InventoryOutbox;
import com.michelet.inventory.infrastructure.repository.JpaInventoryOutboxRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryOutboxHelper {

    private final JpaInventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // 비즈니스 로직(재고 차감 등)과 동일한 트랜잭션으로 묶여서 실패 시 함께 롤백됨
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(String aggregateType, String aggregateId, String eventType, Object payloadObj) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payloadObj);
            InventoryOutbox outbox = InventoryOutbox.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .build();
            outboxRepository.save(outbox);
            log.info("[Inventory Outbox] 이벤트 적재 완료: type={}, id={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Outbox 페이로드 직렬화 실패. aggregateId={}, eventType={}", aggregateId, eventType, e);
            throw new RuntimeException("Outbox 이벤트 생성 중 오류가 발생했습니다.", e);
        }
    }

    // 2단계 스케줄러에서 상태 업데이트 시 사용할 독립 트랜잭션 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(UUID outboxId) {
        outboxRepository.findById(outboxId).ifPresentOrElse(
            InventoryOutbox::markAsPublished,
            () -> log.warn("[Inventory Outbox] 발행 성공 후 상태 변경 대상이 없습니다. outboxId={}", outboxId)
        );
    }
}
