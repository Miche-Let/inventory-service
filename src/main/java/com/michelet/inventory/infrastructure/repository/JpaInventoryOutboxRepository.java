package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.InventoryOutbox;
import com.michelet.inventory.domain.model.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaInventoryOutboxRepository extends JpaRepository<InventoryOutbox, UUID> {

    // OOM 방지 및 생성 시간순 처리를 위한 Top N 쿼리
    List<InventoryOutbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
