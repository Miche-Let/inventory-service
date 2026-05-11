package com.michelet.inventory.infrastructure.config;

import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("perf")
@RequiredArgsConstructor
public class PerfDataInitializer implements ApplicationRunner {

    private final StockRepository stockRepository;

    // 타격 타겟 고정 Option ID
    public static final UUID TEST_OPTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            if (stockRepository.findById(TEST_OPTION_ID).isEmpty()) {
                log.info("[PerfDataInitializer]-inventory : 1차 부하테스트용 무한 재고(100만)를 세팅함");
                Stock stock = Stock.create(
                    TEST_OPTION_ID,
                    1000000, // totalQuantity
                    1000000, // dailyLimit
                    100      // maxLimit
                );
                stockRepository.save(stock);
                log.info("[PerfDataInitializer] 재고 세팅 완료. OptionId: {}", TEST_OPTION_ID);
            }
        } catch (Exception ex) {
            log.error("[PerfDataInitializer] 특정 옵션 재고 초기화 실패. 대상 옵션아이디 : {}.", TEST_OPTION_ID, ex);
        }
    }
}
