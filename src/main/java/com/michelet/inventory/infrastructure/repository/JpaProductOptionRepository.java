package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.ProductOption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductOptionRepository extends JpaRepository<ProductOption, UUID> {
}
