package com.michelet.inventory.domain.model;

import com.michelet.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "p_inventory_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryOutbox extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false, length = 50)
    private String aggregateId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount = 0; // 재시도 횟수 관리 (기본값 0)

    @Version // 스케줄러 동시성 제어를 위한 낙관적 락
    private Long version;

    @Builder
    private InventoryOutbox(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.INIT;
        this.retryCount = 0;
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }

    // 영구 실패 상태 전이
    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
    }

    // 재시도 횟수 증가 로직
    public void incrementRetryCount() {
        this.retryCount++;
    }
}
