package com.michelet.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.michelet.inventory.domain.model.InventoryOutbox;
import com.michelet.inventory.domain.model.OutboxStatus;
import com.michelet.inventory.domain.repository.InventoryOutboxRepository;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryOutboxHelper {

    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${inventory.outbox.max-retries:3}")
    private int maxRetries;

    // 시작 시점에 maxRetries 값 검증
    @PostConstruct
    public void validateMaxRetries() {
        if (maxRetries < 1) {
            throw new IllegalStateException("inventory.outbox.max-retries 설정 오류: 반드시 1 이상이어야 합니다.");
        }
    }

    // MANDATORY로 변경하여 부모 트랜잭션이 없으면 즉각 실패하도록 원자성 강제
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateId, String eventType, Object payloadObj) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType은 필수입니다.");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType은 필수입니다.");
        }
        if (payloadObj == null) {
            throw new IllegalArgumentException("payloadObj는 필수입니다.");
        }

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
            outbox -> {
                if (outbox.getStatus() != OutboxStatus.INIT) {
                    return;
                }
                outbox.markAsPublished();
                outboxRepository.save(outbox);
            },
            () -> log.warn("[Inventory Outbox] 상태 변경 대상이 없습니다. id={}", outboxId)
        );
    }

    // 비동기 실패 시 재시도 횟수 및 상태 관리 로직
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(UUID outboxId) {
        outboxRepository.findById(outboxId).ifPresent(outbox -> {
            if (outbox.getStatus() != OutboxStatus.INIT) {
                return;
            }
            outbox.incrementRetryCount();
            if (outbox.getRetryCount() >= maxRetries) { // 3번 이상 실패 시 영구 실패 처리
                outbox.markAsFailed();
                log.error("[CRITICAL] Outbox 발행 영구 실패. 수동 확인 요망! id={}", outboxId);
            }
            outboxRepository.save(outbox);
        });
    }
}
