package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.Stock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, UUID> {
}
