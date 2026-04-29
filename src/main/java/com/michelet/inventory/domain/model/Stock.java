package com.michelet.inventory.domain.model;

import com.michelet.common.entity.BaseEntity;
import com.michelet.inventory.domain.model.vo.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity(name = "p_stocks")
@Table(name = "p_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity implements Persistable<UUID> {

    @Id
    private UUID optionId; // ProductOption의 ID를 PK로 사용

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer dailyLimit;

    @Column(nullable = false)
    private Integer currentDailyStock;

    @Column(nullable = false)
    private Integer maxLimit;

    @Version // 낙관적 락용 버전
    private Long version;

    @Builder(access = AccessLevel.PRIVATE)
    private Stock(UUID optionId, Integer totalQuantity, Integer dailyLimit, Integer maxLimit) {
        this.optionId = optionId;
        this.totalQuantity = totalQuantity;
        this.dailyLimit = dailyLimit;
        // 생성자 로직: 초기 재고는 일일 한도와 동일하게 설정
        this.currentDailyStock = dailyLimit;
        this.maxLimit = maxLimit != null ? maxLimit : 10;
    }

    public static Stock create(UUID optionId, Integer totalQuantity, Integer dailyLimit, Integer maxLimit) {
        // 1. 기본값 적용 먼저 (null 대응)
        if (optionId == null) {
            throw new IllegalArgumentException("Option ID는 필수입니다.");
        }
        Integer actualMaxLimit = (maxLimit == null) ? 10 : maxLimit;

        // 2. VO를 통한 비즈니스 규칙 검증 - 생성하는 시점에 VO를 호출하여 0 미만인지 검증
        new Quantity(totalQuantity);
        new Quantity(dailyLimit);
        new Quantity(actualMaxLimit);

        // 빌더가 위에서 정의한 private Stock 생성자를 호출하므로 로직이 보장됨
        return Stock.builder()
            .optionId(optionId)
            .totalQuantity(totalQuantity)
            .dailyLimit(dailyLimit)
            .maxLimit(actualMaxLimit)
            .build();
    }

    public void reserve(Integer requestQuantity) {
        validateReserve(requestQuantity);
        this.totalQuantity -= requestQuantity;
        this.currentDailyStock -= requestQuantity;
    }

    private void validateReserve(Integer requestQuantity) {
        if (requestQuantity == null || requestQuantity <= 0) {
            throw new IllegalArgumentException("구매 수량은 1개 이상이어야 합니다.");
        }

        if (this.totalQuantity < requestQuantity) {
            throw new IllegalArgumentException("전체 재고 부족");
        }
        if (this.currentDailyStock < requestQuantity) {
            throw new IllegalArgumentException("일일 판매 한도 초과");
        }
        if (requestQuantity > this.maxLimit) {
            throw new IllegalArgumentException("1인당 최대 구매 수량 초과");
        }
    }

    @Override
    public UUID getId() {
        return optionId;
    }

    @Override
    public boolean isNew() {
        // createdAt은 DB 저장 후에 채워지므로, 객체 생성 직후엔 version(null)으로 판단하는 것이 더 정확함
        return version == null;
    }
}
