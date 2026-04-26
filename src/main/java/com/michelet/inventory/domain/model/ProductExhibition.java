package com.michelet.inventory.domain.model;

import com.michelet.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "p_product_exhibitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductExhibition extends BaseEntity implements Persistable<UUID> {

    @Id
    private UUID productId; // 상품 ID를 PK로 사용

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Product의 ID를 이 엔티티의 ID로 매핑
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @Builder
    private ProductExhibition(Product product, LocalDateTime startAt, LocalDateTime endAt) {
        this.product = product;
        this.productId = product.getId();
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static ProductExhibition create(Product product, LocalDateTime startAt, LocalDateTime endAt) {
        return ProductExhibition.builder()
            .product(product)
            .startAt(startAt)
            .endAt(endAt)
            .build();
    }

    @Override
    public UUID getId() {
        return productId;
    }

    @Override
    public boolean isNew() {
        // BaseEntity의 createdAt이 null이면 아직 저장되지 않은 '새 객체'로 판단함
        return getCreatedAt() == null;
    }
}
