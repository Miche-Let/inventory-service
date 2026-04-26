package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.ProductOption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {
}
