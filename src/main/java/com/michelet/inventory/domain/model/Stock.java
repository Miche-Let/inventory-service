package com.michelet.inventory.domain.model;

import com.michelet.common.entity.BaseEntity;
import com.michelet.inventory.domain.exception.MaxLimitExceededException;
import com.michelet.inventory.domain.exception.OutOfStockException;
import com.michelet.inventory.domain.exception.SoldOutException;
import com.michelet.inventory.domain.model.vo.Quantity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "p_stocks")
@Table(name = "p_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

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

    // Redisson 분산 락을 사용하므로 @Version(낙관적 락) 필드 삭제

    @Builder(access = AccessLevel.PRIVATE)
    private Stock(UUID optionId, Integer totalQuantity, Integer dailyLimit, Integer maxLimit) {
        this.optionId = optionId;
        this.totalQuantity = totalQuantity;
        this.dailyLimit = dailyLimit;
        // 초기 재고 설정 시 일일 한도와 총 재고 중 작은 값으로 설정하여 논리적 모순 방지
        this.currentDailyStock = Math.min(dailyLimit, totalQuantity);
        this.maxLimit = maxLimit != null ? maxLimit : 10;
    }

    public static Stock create(UUID optionId, Integer totalQuantity, Integer dailyLimit, Integer maxLimit) {
        // 1. 기본값 적용 먼저 (null 대응)
        if (optionId == null) {
            throw new IllegalArgumentException("Option ID는 필수입니다.");
        }
        Integer actualMaxLimit = (maxLimit == null) ? 10 : maxLimit;

        // 2. VO를 통한 비즈니스 규칙 검증
        Quantity.validate(totalQuantity);
        Quantity.validate(dailyLimit);
        Quantity.validate(actualMaxLimit);

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
            throw new SoldOutException();
        }
        if (this.currentDailyStock < requestQuantity) {
            throw new OutOfStockException();
        }
        if (requestQuantity > this.maxLimit) {
            throw new MaxLimitExceededException(this.maxLimit);
        }
    }

    // 복구 로직
    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복구 수량은 0보다 커야 합니다.");
        }
        // 1. 전체 재고 복구
        this.totalQuantity = new Quantity(this.totalQuantity).plus(quantity).value();

        // 2. 일일 재고 복구 (기존 값 + 복구량)
        int expectedDailyStock = this.currentDailyStock + quantity;

        // (계산된 일일 재고, 일일 한도, 방금 복구된 총 재고) 중 가장 작은 값을 선택
        int restoredDailyStock = Math.min(expectedDailyStock, Math.min(this.dailyLimit, this.totalQuantity));

        // 3. 필드 업데이트
        this.currentDailyStock = new Quantity(restoredDailyStock).value();
    }
}
