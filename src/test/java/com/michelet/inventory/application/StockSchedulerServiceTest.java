package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.infrastructure.repository.JpaStockRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockSchedulerServiceTest {

    @Autowired
    private StockSchedulerService stockSchedulerService;

    @Autowired
    private JpaStockRepository jpaStockRepository;

    @Test
    @DisplayName("일일 재고 초기화 스케줄러 동작 테스트")
    void resetDailyStocks() {
        // given: dailyLimit은 50인데 현재 재고가 10으로 깎여있는 데이터 세팅
        UUID optionId = UUID.randomUUID();
        Stock stock = Stock.create(optionId, 100, 50, 50);
        stock.reserve(40); // 40개 차감 -> currentDailyStock이 10이 됨
        jpaStockRepository.saveAndFlush(stock);

        // when: 자정이 되었다고 가정하고 스케줄러 메서드를 강제로 직접 호출!
        stockSchedulerService.resetDailyStocks();

        // then: DB에서 다시 조회했을 때 일일 재고가 50으로 꽉 차있어야 함
        Stock resetStock = jpaStockRepository.findById(optionId).orElseThrow();
        assertThat(resetStock.getCurrentDailyStock()).isEqualTo(50);
    }
}
