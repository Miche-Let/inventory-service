package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
