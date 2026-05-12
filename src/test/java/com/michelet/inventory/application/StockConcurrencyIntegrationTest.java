package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.infrastructure.repository.JpaStockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test") //PostgreSQL Testcontainer 사용
class StockConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    // Redisson 분산 락 테스트를 위해 Redis 컨테이너 추가
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        // Redis 설정 주입
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // 서비스가 아닌 '락 파사드(Facade)'를 주입받아 동시성 테스트 진행
    @Autowired
    private StockLockFacade stockLockFacade;

    // 조회 로직을 위한 도메인 레포지토리
    @Autowired
    private StockRepository stockRepository;

    // 테스트 클린업을 위한 JpaRepository 직접 의존
    @Autowired
    private JpaStockRepository jpaStockRepository;

    // 테스트 환경에는 실제 카프카가 없으니까 가짜 객체로 덮어씌워서 타임아웃 에러를 방지
    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private UUID testOptionId;

    @BeforeEach
    void setUp() {
        testOptionId = UUID.randomUUID();
        // 초기 재고: 총 200개, 일일 150개, 1인당 제한 10개
        Stock stock = Stock.create(testOptionId, 200, 150, 10);
        stockRepository.save(stock);

        // NPE 방지: KafkaTemplate.send() Mock 설정
        given(kafkaTemplate.send(anyString(), anyString(), any()))
            .willReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() {
        // 도메인 레포지토리가 아닌 인프라 레포지토리를 통해 클린업
        jpaStockRepository.deleteAll();
    }

    @Test
    @DisplayName("동시성: 10명이 동시에 1개씩 재고 차감을 시도하여, 성공한 횟수만큼 정확히 재고가 줄어든다.")
    void reserveStock_Concurrency() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        // 모든 쓰레드가 동시에 출발하도록 제어하는 startLatch
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // 성공한 횟수를 안전하게 카운트하기 위한 변수
        AtomicInteger successCount = new AtomicInteger();

        ReserveStockRequest request = new ReserveStockRequest(testOptionId, 1, null);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    // 모든 작업 쓰레드는 여기서 대기하며 신호를 기다림
                    startLatch.await();
                    // 락 획득 메서드 호출
                    stockLockFacade.reserveStockWithLock(request);
                    successCount.incrementAndGet(); // 에러 없이 통과하면 성공 횟수 1 증가
                } catch (Exception e) {
                    System.out.println("차감 실패: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        // 대기 중이던 쓰레드를 동시에 실행 시작 (출발 신호)
        startLatch.countDown();

        try {
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).withFailMessage("쓰레드 작업 타임아웃").isTrue();
        } finally {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }

        // then
        Stock findStock = stockRepository.findById(testOptionId).orElseThrow();
        int actualSuccess = successCount.get();

        System.out.println("최종 성공 횟수: " + actualSuccess + " / " + threadCount);

        // 단 한 번도 성공하지 못한 경우(실제로는 버그)를 무조건 통과시켜버리는 False Positive 방지
        assertThat(actualSuccess).isGreaterThan(0);
        // 성공 횟수가 전체 스레드 수를 초과할 수 없음
        assertThat(actualSuccess).isLessThanOrEqualTo(threadCount);

        // 200개에서 성공한 횟수만큼 차감되었는지 검증
        assertThat(findStock.getTotalQuantity()).isEqualTo(200 - actualSuccess);
        // 150개에서 성공한 횟수만큼 차감되었는지 검증
        assertThat(findStock.getCurrentDailyStock()).isEqualTo(150 - actualSuccess);
    }
}
