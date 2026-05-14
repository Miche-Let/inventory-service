package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.ProcessedEvent;
import com.michelet.inventory.domain.repository.ProcessedEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final JpaProcessedEventRepository jpaRepository;

    @Override
    public boolean existsById(UUID eventId) {
        return jpaRepository.existsById(eventId);
    }

    @Override
    public ProcessedEvent save(ProcessedEvent event) {
        return jpaRepository.save(event);
    }
}
