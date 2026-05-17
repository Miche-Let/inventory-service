package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.infrastructure.repository.JpaStockRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers
class StockSchedulerServiceTest {

    // 테스트 실행 시 가짜 미니 Redis 컨테이너 띄우기 (인프라 환경과 동일한 버전)
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    // 띄워진 가짜 Redis의 동적 IP와 포트를 스프링 환경변수에 주입
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // Kafka 연결 에러(로그 도배 및 실패)를 막기 위해 가짜 템플릿 주입
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

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
