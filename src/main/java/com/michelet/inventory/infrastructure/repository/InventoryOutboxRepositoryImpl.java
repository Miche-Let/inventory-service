package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.InventoryOutbox;
import com.michelet.inventory.domain.model.OutboxStatus;
import com.michelet.inventory.domain.repository.InventoryOutboxRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InventoryOutboxRepositoryImpl implements InventoryOutboxRepository {

    private final JpaInventoryOutboxRepository jpaRepository;

    @Override
    public InventoryOutbox save(InventoryOutbox outbox) {
        return jpaRepository.save(outbox);
    }

    @Override
    public Optional<InventoryOutbox> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<InventoryOutbox> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status) {
        return jpaRepository.findTop50ByStatusOrderByCreatedAtAsc(status);
    }
}
