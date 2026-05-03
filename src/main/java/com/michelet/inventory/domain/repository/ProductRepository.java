package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.Product;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(UUID id);

    Slice<Product> findProductsToOpen(LocalDateTime now, Pageable pageable);

    Slice<Product> findProductsToClose(LocalDateTime now, Pageable pageable);
}
