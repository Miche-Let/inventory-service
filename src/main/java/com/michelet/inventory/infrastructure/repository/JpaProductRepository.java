package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.Product;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaProductRepository extends JpaRepository<Product, UUID> {

    // 전시 시작 시간이 지났는데 아직 숨김(HIDDEN)인 상품 조회
    @Query("SELECT p FROM Product p JOIN ProductExhibition e ON p.id = e.productId " +
        "WHERE p.status = 'HIDDEN' AND e.startAt <= :now AND (e.endAt IS NULL OR e.endAt > :now) " +
        "ORDER BY p.id ASC")
    Slice<Product> findProductsToOpen(@Param("now") LocalDateTime now, Pageable pageable);

    // 전시 종료 시간이 지났는데 아직 판매 중(ACTIVE)인 상품 조회
    @Query("SELECT p FROM Product p JOIN ProductExhibition e ON p.id = e.productId " +
        "WHERE p.status = 'ACTIVE' AND e.endAt <= :now " +
        "ORDER BY p.id ASC")
    Slice<Product> findProductsToClose(@Param("now") LocalDateTime now, Pageable pageable);
}
