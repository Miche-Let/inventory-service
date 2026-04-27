package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductCategory;
import com.michelet.inventory.domain.repository.ProductExhibitionRepository;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import com.michelet.inventory.domain.repository.ProductRepository;
import com.michelet.inventory.domain.repository.StockRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test") // 테스트용 H2 설정 사용
@Transactional // 테스트 후 롤백하여 DB 청결 유지
class ProductCommandServiceTest {

    @Autowired
    private ProductCommandService productCommandService;

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductOptionRepository productOptionRepository;
    @Autowired
    private ProductExhibitionRepository productExhibitionRepository;
    @Autowired
    private StockRepository stockRepository;

    @Test
    @DisplayName("성공: 상품 등록 시 모든 도메인 모델(상품/전시/옵션/재고)이 올바르게 저장되어야 한다")
    void createProductIntegrationTest() {
        // given
        CreateProductCommand command = new CreateProductCommand(
            UUID.randomUUID(),
            "미슐랭 밀키트 세트",
            ProductCategory.MEALKIT,
            new BigDecimal("45000"),
            Map.of("servings", 2, "cookingTime", "20min"),
            new CreateProductCommand.ExhibitionCommand(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(30)
            ),
            List.of(
                new CreateProductCommand.OptionCommand("맵기 보통", BigDecimal.ZERO, 100, 20, 2),
                new CreateProductCommand.OptionCommand("맵기 매움", new BigDecimal("1000"), 80, 15, 1)
            )
        );

        // when: 서비스 호출 시 ProductResult를 받음
        ProductResult result = productCommandService.createProduct(command);

        // then: 4개 테이블 데이터 검증 - result에서 productId를 꺼내어 검증
        UUID productId = result.productId();
        // 1. 상품 테이블 확인
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName()).isEqualTo("미슐랭 밀키트 세트");
        assertThat(product.getAttributes().get("servings")).isEqualTo(2);

        // 2. 전시 정보 확인 (1:1)
        assertThat(productExhibitionRepository.existsById(productId)).isTrue();

        // 3. 옵션 확인 (1:N)
        var options = productOptionRepository.findAll().stream()
            .filter(o -> o.getProduct().getId().equals(productId))
            .sorted(java.util.Comparator.comparing(
                com.michelet.inventory.domain.model.ProductOption::getName)) // 이름순 정렬 후 검증
            .toList();
        assertThat(options).hasSize(2);

        // 4. 재고 확인 (각 옵션의 이름에 맞는 정확한 수량 검증)
        options.forEach(option -> {
            var stock = stockRepository.findById(option.getId()).orElseThrow();
            if (option.getName().equals("맵기 보통")) {
                assertThat(stock.getTotalQuantity()).isEqualTo(100);
                assertThat(stock.getCurrentDailyStock()).isEqualTo(20);
            } else if (option.getName().equals("맵기 매움")) {
                assertThat(stock.getTotalQuantity()).isEqualTo(80);
                assertThat(stock.getCurrentDailyStock()).isEqualTo(15);
                assertThat(stock.getMaxLimit()).isEqualTo(1);
            }
        });
    }

    @Test
    @DisplayName("실패: 상품 가격이 음수일 경우 비즈니스 예외(Price VO)가 발생해야 한다")
    void createProduct_Fail_NegativePrice() {
        // given: 가격을 -1000원으로 설정
        CreateProductCommand command = new CreateProductCommand(
            UUID.randomUUID(), "불량 밀키트", ProductCategory.MEALKIT,
            new BigDecimal("-1000"),
            Map.of("servings", 2),
            new CreateProductCommand.ExhibitionCommand(LocalDateTime.now(), LocalDateTime.now().plusDays(1)),
            List.of(new CreateProductCommand.OptionCommand("옵션", BigDecimal.ZERO, 10, 5, 1))
        );

        // when & then: Price VO에서 IllegalArgumentException이 발생하는지 검증
        assertThatThrownBy(() ->
            productCommandService.createProduct(command)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("가격은 0원 이상이어야 합니다.");
    }
}
