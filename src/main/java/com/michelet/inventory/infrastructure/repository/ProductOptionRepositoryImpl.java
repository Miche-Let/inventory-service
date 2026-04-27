package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.ProductOption;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductOptionRepositoryImpl implements ProductOptionRepository {
    private final JpaProductOptionRepository jpaRepository;

    @Override
    public ProductOption save(ProductOption option) {
        return jpaRepository.save(option);
    }

    @Override
    public List<ProductOption> saveAll(List<ProductOption> options) {
        return jpaRepository.saveAll(options);
    }

    @Override
    public List<ProductOption> findAll() {
        return jpaRepository.findAll();
    }
}
