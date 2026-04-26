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

@Entity
@Table(name = "p_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity implements Persistable<UUID> {

    @Id
    private UUID optionId; // ProductOption의 ID를 PK로 사용

    @Column(nullable = false)
    private Integer totalQuantity = 0;

    @Column(nullable = false)
    private Integer dailyLimit = 0;

    @Column(nullable = false)
    private Integer currentDailyStock = 0;

    @Column(nullable = false)
    private Integer maxLimit = 10;

    @Version // 낙관적 락용 버전
    private Long version;

    @Builder
    private Stock(UUID optionId, Integer totalQuantity, Integer dailyLimit, Integer maxLimit) {
        this.optionId = optionId;
        this.totalQuantity = totalQuantity;
        this.dailyLimit = dailyLimit;
        this.currentDailyStock = dailyLimit; // 초기값은 dailyLimit과 동일하게 세팅
        this.maxLimit = maxLimit != null ? maxLimit : 10;
    }

    public static Stock create(UUID optionId, Integer totalQuantity, Integer dailyLimit, Integer maxLimit) {
        // 1. 기본값 적용 먼저 (null 대응)
        Integer actualMaxLimit = (maxLimit == null) ? 10 : maxLimit;

        // 2. VO를 통한 비즈니스 규칙 검증 - 생성하는 시점에 VO를 호출하여 0 미만인지 검증
        new Quantity(totalQuantity);
        new Quantity(dailyLimit);
        new Quantity(maxLimit);

        return Stock.builder()
            .optionId(optionId)
            .totalQuantity(totalQuantity)
            .dailyLimit(dailyLimit)
            .maxLimit(maxLimit)
            .build();
    }

    // 재고 규칙 - 검증 메서드
    public void validateReserve(Integer requestQuantity) {
        // 입력값 유효성 선제 검증
        if (requestQuantity == null || requestQuantity <= 0) {
            throw new IllegalArgumentException("예약 수량은 1개 이상이어야 합니다.");
        }

        if (this.totalQuantity < requestQuantity) {
            throw new RuntimeException("전체 재고 부족");
        }
        if (this.currentDailyStock < requestQuantity) {
            throw new RuntimeException("일일 판매 한도 초과");
        }
        if (requestQuantity > this.maxLimit) {
            throw new RuntimeException("1인당 최대 구매 수량 초과");
        }
    }

    @Override
    public UUID getId() {
        return optionId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
