package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.Product;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProductRepository extends JpaRepository<Product, UUID> {
}
