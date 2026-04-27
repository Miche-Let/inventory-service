package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.Stock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaStockRepository extends JpaRepository<Stock, UUID> {
}
