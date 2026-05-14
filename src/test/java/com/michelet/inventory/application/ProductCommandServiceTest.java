package com.michelet.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.application.dto.ProductCreatedEvent;
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

    // KafkaTemplate 대신 OutboxHelper 주입
    @Mock
    private InventoryOutboxHelper outboxHelper;

    @Captor
    private ArgumentCaptor<ProductCreatedEvent> eventCaptor;

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
    @DisplayName("성공: 상품 등록 시 모든 도메인 모델이 올바른 값으로 저장되고 Outbox에 적재되어야 한다")
    void createProductUnitTest() {
        // given
        LocalDateTime now = LocalDateTime.now();
        CreateProductCommand command = new CreateProductCommand(
            UUID.randomUUID(),
            "미슐랭 밀키트 세트",
            ProductCategory.MEALKIT,
            new BigDecimal("45000"),
            Map.of("servings", 2, "cookingTime", "20min"),
            new CreateProductCommand.ExhibitionCommand(
                now.plusDays(1),
                now.plusDays(30)
            ),
            List.of(
                new CreateProductCommand.OptionCommand("맵기 보통", BigDecimal.ZERO, 100, 20, 2),
                new CreateProductCommand.OptionCommand("맵기 매움", new BigDecimal("1000"), 80, 15, 1)
            )
        );

        // 1. Product 저장 시 ID 자동 생성 모킹
        given(productRepository.save(any(Product.class))).willAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
            return product;
        });

        // 2. ProductOption 리스트 저장 시 각 객체에 ID 자동 생성 모킹
        given(productOptionRepository.saveAll(any())).willAnswer(invocation -> {
            List<ProductOption> options = invocation.getArgument(0);
            for (ProductOption option : options) {
                org.springframework.test.util.ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
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

        // 1. 반환 결과(ProductResult) 및 상품 검증
        Product savedProduct = productCaptor.getValue();

        // Kafka 직접 전송 대신 Outbox 적재 여부 검증
        verify(outboxHelper, times(1)).append(
            eq("PRODUCT"),
            eq(savedProduct.getId().toString()),
            eq("PRODUCT_CREATED"),
            eventCaptor.capture()
        );

        ProductCreatedEvent capturedEvent = eventCaptor.getValue();

        // 캡처된 Kafka 이벤트의 페이로드 전체 필드 정밀 검증
        assertThat(capturedEvent.productId()).isEqualTo(savedProduct.getId());
        assertThat(capturedEvent.name()).isEqualTo("미슐랭 밀키트 세트");
        assertThat(capturedEvent.category()).isEqualTo("MEALKIT");
        assertThat(capturedEvent.basePrice()).isEqualByComparingTo(new BigDecimal("45000"));
        assertThat(capturedEvent.attributes()).containsEntry("servings", 2).containsEntry("cookingTime", "20min");

        // 옵션이 정확히 DTO로 변환되었는지 검증
        assertThat(capturedEvent.options()).hasSize(2);
        assertThat(capturedEvent.options().get(0).name()).isEqualTo("맵기 보통");
        assertThat(capturedEvent.options().get(0).totalQuantity()).isEqualTo(100);
        assertThat(capturedEvent.options().get(0).currentDailyStock()).isEqualTo(20);
        assertThat(capturedEvent.options().get(0).dailyLimit()).isEqualTo(20);

        assertThat(result).isNotNull();
        assertThat(result.productId()).isEqualTo(savedProduct.getId());
    }

    @Test
    @DisplayName("실패: 상품 가격이 음수일 경우 비즈니스 예외(Price VO)가 발생해야 한다")
    void createProduct_Fail_NegativePrice() {
        // given: 가격을 -1000원으로 설정
        LocalDateTime now = LocalDateTime.now();
        CreateProductCommand command = new CreateProductCommand(
            UUID.randomUUID(), "불량 밀키트", ProductCategory.MEALKIT,
            new BigDecimal("-1000"),
            Map.of("servings", 2),
            new CreateProductCommand.ExhibitionCommand(now, now.plusDays(1)),
            List.of(new CreateProductCommand.OptionCommand("옵션", BigDecimal.ZERO, 10, 5, 1))
        );

        // when & then: Price VO에서 IllegalArgumentException이 발생하는지 검증
        assertThatThrownBy(() ->
            productCommandService.createProduct(command)
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("가격은 0원 이상이어야 합니다.");
    }
}
