package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.InventoryOutbox;
import com.michelet.inventory.domain.model.OutboxStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryOutboxRepository {
    InventoryOutbox save(InventoryOutbox outbox);

    Optional<InventoryOutbox> findById(UUID id);

    // 스케줄러에서 사용할 배치 조회 메서드
    List<InventoryOutbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
