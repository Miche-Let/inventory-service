package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.ProcessedEvent;
import java.util.UUID;

public interface ProcessedEventRepository {
    boolean existsById(UUID eventId);

    ProcessedEvent save(ProcessedEvent event);
}
