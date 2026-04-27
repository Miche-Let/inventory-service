package com.michelet.inventory.domain.model;

import com.michelet.common.entity.BaseEntity;
import com.michelet.inventory.domain.model.vo.Price;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_product_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal addPrice = BigDecimal.ZERO;

    @Builder
    private ProductOption(Product product, String name, BigDecimal addPrice) {
        this.product = product;
        this.name = name;
        this.addPrice = addPrice != null ? addPrice : BigDecimal.ZERO;
    }

    public static ProductOption create(Product product, String name, BigDecimal addPrice) {

        // 가격 검증 VO 호출
        new Price(addPrice);

        return ProductOption.builder()
            .product(product)
            .name(name)
            .addPrice(addPrice)
            .build();
    }
}
