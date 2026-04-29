package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test") //PostgreSQL Testcontainer 사용
class StockConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        // 테스트 시 Kafka 연결을 끊어 오류 방지
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private StockCommandService stockCommandService;

    @Autowired
    private StockRepository stockRepository;

    private UUID testOptionId;

    @BeforeEach
    void setUp() {
        testOptionId = UUID.randomUUID();
        // 초기 재고: 총 200개, 일일 150개, 1인당 제한 10개
        Stock stock = Stock.create(testOptionId, 200, 150, 10);
        stockRepository.save(stock);
    }

    @AfterEach
    void tearDown() {
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("동시성: 100명이 동시에 1개씩 재고 차감을 시도하여, 성공한 횟수만큼 정확히 재고가 줄어든다.")
    void reserveStock_Concurrency() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 성공한 횟수를 안전하게 카운트하기 위한 변수
        AtomicInteger successCount = new AtomicInteger();

        ReserveStockRequest request = new ReserveStockRequest(testOptionId, 1);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockCommandService.reserveStockWithRetry(request);
                    successCount.incrementAndGet(); // 에러 없이 통과하면 성공 횟수 1 증가
                } catch (Exception e) {
                    // 낙관적 락 재시도 3회 모두 실패한 스레드들은 예외를 던지며 이곳으로 옴
                    System.out.println("차감 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        Stock findStock = stockRepository.findById(testOptionId).orElseThrow();
        int actualSuccess = successCount.get();

        System.out.println("최종 성공 횟수: " + actualSuccess + " / " + threadCount);

        // 200개에서 성공한 횟수만큼 차감되었는지 검증
        assertThat(findStock.getTotalQuantity()).isEqualTo(200 - actualSuccess);
        // 150개에서 성공한 횟수만큼 차감되었는지 검증
        assertThat(findStock.getCurrentDailyStock()).isEqualTo(150 - actualSuccess);
    }
}
