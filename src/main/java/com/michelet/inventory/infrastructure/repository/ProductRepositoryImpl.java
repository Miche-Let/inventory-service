package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.repository.ProductRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final JpaProductRepository jpaRepository;

    @Override
    public Product save(Product product) {
        return jpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Slice<Product> findProductsToOpen(LocalDateTime now, Pageable pageable) {
        return jpaRepository.findProductsToOpen(now, pageable);
    }

    @Override
    public Slice<Product> findProductsToClose(LocalDateTime now, Pageable pageable) {
        return jpaRepository.findProductsToClose(now, pageable);
    }
}
