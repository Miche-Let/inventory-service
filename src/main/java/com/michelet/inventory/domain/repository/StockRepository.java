package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.Stock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
    Stock save(Stock stock);

    List<Stock> saveAll(List<Stock> stocks);

    Optional<Stock> findById(UUID id);

    void deleteAll();
}
