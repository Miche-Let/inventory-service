package com.michelet.inventory.infrastructure.repository;

import com.michelet.inventory.domain.model.Stock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface JpaStockRepository extends JpaRepository<Stock, UUID> {

    // 일일재고복구 - 벌크 업데이트 쿼리
    // dailyLimit, totalQuantity 중 작은 값으로 리셋함
    @Modifying(clearAutomatically = true)
    @Query("UPDATE p_stocks s " +
        "SET s.currentDailyStock = LEAST(s.dailyLimit, s.totalQuantity) " +
        "WHERE s.currentDailyStock <> LEAST(s.dailyLimit, s.totalQuantity)")
    int resetDailyStock();
}
