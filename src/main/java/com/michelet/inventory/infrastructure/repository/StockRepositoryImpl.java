package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {
    private final JpaStockRepository jpaRepository;

    @Override
    public Stock save(Stock stock) {
        return jpaRepository.save(stock);
    }

    @Override
    public List<Stock> saveAll(List<Stock> stocks) {
        return jpaRepository.saveAll(stocks);
    }

    @Override
    public Optional<Stock> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void deleteAll() {
        jpaRepository.deleteAll();
    }
}
