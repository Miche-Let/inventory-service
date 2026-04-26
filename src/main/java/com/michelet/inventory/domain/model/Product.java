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
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    private Product(UUID restaurantId, String name, ProductCategory category, BigDecimal basePrice,
                    Map<String, Object> attributes) {

        // 도메인 불변성 검증 (Fail-Fast)
        Objects.requireNonNull(restaurantId, "Restaurant ID must not be null");
        Objects.requireNonNull(category, "Category must not be null");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be blank");
        }

        this.restaurantId = restaurantId;
        this.name = name;
        this.category = category;
        this.basePrice = validateOrNormalizePrice(basePrice);
        // 방어적 복사 및 null 방어
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
        this.status = ProductStatus.ACTIVE;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    private BigDecimal validateOrNormalizePrice(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0원 이상이어야 합니다.");
        }
        // 소수점 2자리로 반올림하여 정규화 (DB scale=2 대응)
        return price.setScale(2, RoundingMode.HALF_UP);
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
