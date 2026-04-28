package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductCategory;
import com.michelet.inventory.domain.model.ProductExhibition;
import com.michelet.inventory.domain.model.ProductOption;
import com.michelet.inventory.domain.model.Stock;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @InjectMocks
    private ProductCommandService productCommandService;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductOptionRepository productOptionRepository;
    @Mock
    private ProductExhibitionRepository productExhibitionRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 리포지토리로 넘어가는 객체를 중간에 가로채기 위한 Captor
    @Captor
    private ArgumentCaptor<Product> productCaptor;
    @Captor
    private ArgumentCaptor<ProductExhibition> exhibitionCaptor;
    @Captor
    private ArgumentCaptor<List<ProductOption>> optionsCaptor;
    @Captor
    private ArgumentCaptor<List<Stock>> stocksCaptor;

    @Test
    @DisplayName("성공: 상품 등록 시 모든 도메인 모델(상품/전시/옵션/재고)이 올바른 값으로 저장소에 전달되어야 한다")
    void createProductUnitTest() {
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

        // 1. Product 저장 시 ID 자동 생성 모킹
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
            return product;
        });

        // 2. ProductOption 리스트 저장 시 각 객체에 ID 자동 생성 모킹
        given(productOptionRepository.saveAll(any())).willAnswer(invocation -> {
            List<ProductOption> options = invocation.getArgument(0);
            for (ProductOption option : options) {
                // JPA가 DB 삽입 후 ID를 채워주는 동작을 리플렉션으로 강제 시뮬레이션
                ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
            }
            return options;
        });

        // when
        ProductResult result = productCommandService.createProduct(command);

        // then: DB에 던져지는 객체들을 캡처
        verify(productRepository).save(productCaptor.capture());
        verify(productExhibitionRepository).save(exhibitionCaptor.capture());
        verify(productOptionRepository).saveAll(optionsCaptor.capture());
        verify(stockRepository).saveAll(stocksCaptor.capture());

        // 1. 상품 검증
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getName()).isEqualTo("미슐랭 밀키트 세트");
        assertThat(savedProduct.getAttributes().get("servings")).isEqualTo(2);

        // 2. 전시 정보 검증
        ProductExhibition savedExhibition = exhibitionCaptor.getValue();
        assertThat(savedExhibition.getProduct()).isEqualTo(savedProduct);
        assertThat(savedExhibition.getStartAt()).isEqualTo(command.exhibition().startAt());

        // 3. 옵션 검증
        List<ProductOption> savedOptions = optionsCaptor.getValue();
        assertThat(savedOptions).hasSize(2);

        // 4. 재고 검증
        List<Stock> savedStocks = stocksCaptor.getValue();
        assertThat(savedStocks).hasSize(2);

        Stock normalStock = savedStocks.get(0);
        assertThat(normalStock.getTotalQuantity()).isEqualTo(100);
        assertThat(normalStock.getCurrentDailyStock()).isEqualTo(20);

        Stock spicyStock = savedStocks.get(1);
        assertThat(spicyStock.getTotalQuantity()).isEqualTo(80);
        assertThat(spicyStock.getCurrentDailyStock()).isEqualTo(15);
        assertThat(spicyStock.getMaxLimit()).isEqualTo(1);
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
