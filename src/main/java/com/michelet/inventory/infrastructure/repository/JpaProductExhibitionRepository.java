package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.ProductExhibition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductExhibitionRepository extends JpaRepository<ProductExhibition, UUID> {
}
