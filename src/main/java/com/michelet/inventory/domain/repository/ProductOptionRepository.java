package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.ProductOption;
import java.util.List;

public interface ProductOptionRepository {
    ProductOption save(ProductOption option);

    List<ProductOption> saveAll(List<ProductOption> options);

    List<ProductOption> findAll();
}
