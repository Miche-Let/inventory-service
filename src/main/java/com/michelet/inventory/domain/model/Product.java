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
    private Map<String, Object> attributes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Builder(access = AccessLevel.PRIVATE) // 외부에서 무분별한 빌더 사용 금지
    private Product(UUID restaurantId, String name, ProductCategory category, BigDecimal basePrice,
                    Map<String, Object> attributes) {

        // 1. 필수 값 검증
        Objects.requireNonNull(restaurantId, "Restaurant ID는 필수입니다.");
        Objects.requireNonNull(category, "카테고리는 필수입니다.");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }

        // 2. 비즈니스 규칙 위임 (Price VO)
        this.restaurantId = restaurantId;
        this.name = name;
        this.category = category;
        this.basePrice = Price.of(basePrice).value(); // VO를 통한 검증/정규화
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
        this.status = ProductStatus.ACTIVE;
    }

    public static Product create(UUID restaurantId, String name, ProductCategory category, BigDecimal basePrice,
                                 Map<String, Object> attributes) {
        return new Product(restaurantId, name, category, basePrice, attributes);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // 전시 상태 변경
    public void changeStatus(ProductStatus newStatus) {
        Objects.requireNonNull(newStatus, "새로운 상태(newStatus)는 null일 수 없습니다.");
        this.status = newStatus;
    }
}
