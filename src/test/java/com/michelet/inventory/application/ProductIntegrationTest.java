package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductCategory;
import com.michelet.inventory.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 테스트 종료 후 데이터 롤백
class ProductIntegrationTest {

    // 1. PostgreSQL 도커 컨테이너 실행
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    // 2. Redis 도커 컨테이너 실행
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.0-alpine").withExposedPorts(6379);

    // 3. 스프링 환경변수에 도커 컨테이너 접속 정보 주입
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // H2 드라이버를 Postgres 드라이버로 강제 덮어쓰기!
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProductCommandService productCommandService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("상품 등록 통합 테스트: 서비스 호출 시 실제 DB에 상품이 저장되어야 한다")
    void createProduct_Integration() {
        // given
        CreateProductCommand command = new CreateProductCommand(
            UUID.randomUUID(),
            "테스트 통합 밀키트",
            ProductCategory.MEALKIT,
            new BigDecimal("45000"),
            Map.of("servings", 2),
            new CreateProductCommand.ExhibitionCommand(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(30)
            ),
            List.of(
                new CreateProductCommand.OptionCommand("기본", BigDecimal.ZERO, 100, 20, 2)
            )
        );

        // when
        ProductResult result = productCommandService.createProduct(command);

        // 강제로 영속성 컨텍스트를 비워 실제 DB에서 조회하도록 보장
        entityManager.flush();
        entityManager.clear();

        // then: 실제 DB(PostgreSQL 도커)에 데이터가 들어갔는지 검증
        assertThat(result).isNotNull();

        Product savedProduct = productRepository.findById(result.productId()).orElseThrow();
        assertThat(savedProduct.getName()).isEqualTo("테스트 통합 밀키트");
        assertThat(savedProduct.getBasePrice()).isEqualByComparingTo(new BigDecimal("45000"));
    }
}
