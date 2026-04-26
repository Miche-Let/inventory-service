package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.ProductExhibition;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductExhibitionRepository extends JpaRepository<ProductExhibition, UUID> {
}
