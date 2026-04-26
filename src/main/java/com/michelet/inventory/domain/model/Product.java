package com.michelet.inventory.domain.model;

import com.michelet.common.entity.BaseEntity;
import com.michelet.inventory.domain.model.vo.Price;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "p_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @JdbcTypeCode(SqlTypes.JSON) // JSONB
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new HashMap<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Builder(access = AccessLevel.PRIVATE)
    private Product(UUID restaurantId, String name, ProductCategory category,
                    BigDecimal basePrice, Map<String, Object> attributes) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.category = category;
        this.basePrice = basePrice;
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.status = ProductStatus.ACTIVE;
    }

    public static Product create(UUID restaurantId, String name, ProductCategory category,
                                 BigDecimal basePrice, Map<String, Object> attributes) {
        // 가격 검증 VO 호출
        new Price(basePrice);

        return Product.builder()
            .restaurantId(restaurantId)
            .name(name)
            .category(category)
            .basePrice(basePrice)
            .attributes(attributes)
            .build();
    }
}
