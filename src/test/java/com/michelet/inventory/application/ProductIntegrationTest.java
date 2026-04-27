package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductCategory;
import com.michelet.inventory.domain.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ProductIntegrationTest {

    // 1. static 블록이나 선언부에서 바로 컨테이너 인스턴스 생성
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("inventory_db")
        .withUsername("testuser")
        .withPassword("testpass");

    // 2. 스프링이 뜨기 전에 수동으로 컨테이너를 먼저 실행
    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("실제 PostgreSQL 환경에서 JSONB 속성이 정상 저장 및 조회되어야 한다")
    void jsonbPersistenceTest() {
        // 1. Given: 테스트 데이터 준비
        Map<String, Object> attributes = Map.of("servings", 2);
        Product product = Product.create(
            UUID.randomUUID(), "테스트", ProductCategory.MEALKIT, new BigDecimal("1000"), attributes
        );

        // 2. When: 저장 (save 메서드는 영속화된 객체를 반환)
        Product savedProduct = productRepository.save(product);

        // 3. Then: 저장된 ID로 정확히 다시 조회
        Product found = productRepository.findById(savedProduct.getId())
            .orElseThrow(() -> new AssertionError("저장된 상품을 찾을 수 없습니다."));

        // 검증
        assertThat(found.getAttributes()).containsEntry("servings", 2);
    }
}
