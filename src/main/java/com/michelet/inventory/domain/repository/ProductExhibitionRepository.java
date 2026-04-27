package com.michelet.inventory.domain.repository;

import com.michelet.inventory.domain.model.ProductExhibition;
import java.util.UUID;

public interface ProductExhibitionRepository {
    ProductExhibition save(ProductExhibition exhibition);

    boolean existsById(UUID id);
}
