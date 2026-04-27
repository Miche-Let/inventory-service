package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.ProductExhibition;
import com.michelet.inventory.domain.repository.ProductExhibitionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductExhibitionRepositoryImpl implements ProductExhibitionRepository {
    private final JpaProductExhibitionRepository jpaRepository;

    @Override
    public ProductExhibition save(ProductExhibition exhibition) {
        return jpaRepository.save(exhibition);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }
}
