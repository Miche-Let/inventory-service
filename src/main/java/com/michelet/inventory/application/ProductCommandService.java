package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.CreateProductCommand;
import com.michelet.inventory.application.dto.ProductCreatedEvent;
import com.michelet.inventory.application.dto.ProductResult;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductExhibition;
import com.michelet.inventory.domain.model.ProductOption;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.ProductExhibitionRepository;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import com.michelet.inventory.domain.repository.ProductRepository;
import com.michelet.inventory.domain.repository.StockRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductExhibitionRepository productExhibitionRepository;
    private final StockRepository stockRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public String checkHealth() {
        return "Inventory Command Service is Healthy";
    }

    @Transactional
    public ProductResult createProduct(CreateProductCommand command) {
        // 1. p_products 저장
        Product product = Product.create(
            command.restaurantId(), command.name(), command.category(),
            command.basePrice(), command.attributes()
        );
        productRepository.save(product);

        // 2. p_product_exhibitions 저장
        productExhibitionRepository.save(ProductExhibition.create(
            product, command.exhibition().startAt(), command.exhibition().endAt()
        ));

        // 3. 옵션(Options) 리스트 생성 및 일괄 저장
        List<ProductOption> options = command.options().stream()
            .map(opt -> ProductOption.create(product, opt.name(), opt.addPrice()))
            .toList();

        // saveAll을 호출하면 ID가 채워진 저장된 리스트가 반환됨
        List<ProductOption> savedOptions = productOptionRepository.saveAll(options);

        // 4. 재고(Stocks) 리스트 및 카프카 이벤트 옵션 DTO 동시 생성
        List<Stock> stocks = new ArrayList<>();
        List<ProductCreatedEvent.OptionEventDto> optionEventDtos = new ArrayList<>();
        // 반복문에서 꺼내 쓸 원본 요청 옵션 리스트
        List<CreateProductCommand.OptionCommand> requestOptions = command.options();

        for (int i = 0; i < savedOptions.size(); i++) {
            CreateProductCommand.OptionCommand reqOption = requestOptions.get(i);
            ProductOption dbOption = savedOptions.get(i);

            // DB 저장을 위한 Stock 객체 생성
            stocks.add(Stock.create(
                dbOption.getId(),
                reqOption.totalQuantity(),
                reqOption.dailyLimit(),
                reqOption.maxLimit()
            ));

            // 카프카 전송을 위한 Event DTO 생성
            optionEventDtos.add(new ProductCreatedEvent.OptionEventDto(
                dbOption.getId(),
                dbOption.getName(),
                dbOption.getAddPrice(),
                reqOption.totalQuantity(),
                reqOption.dailyLimit() // currentDailyStock은 dailyLimit으로 초기화
            ));
        }
        stockRepository.saveAll(stocks);

        // 5. 카프카 이벤트 페이로드 조립
        ProductCreatedEvent event = new ProductCreatedEvent(
            product.getId(), // 저장 후 발급된 상품 ID
            command.restaurantId(),
            command.name(),
            command.category().name(),
            command.attributes(),
            command.exhibition().startAt(),
            command.exhibition().endAt(),
            optionEventDtos
        );

        // 6. product.created 토픽으로 메시지 발행 (Partition Key로 productId 사용)
        log.info("상품 등록 이벤트 발행 시작: productId={}", event.productId());
        kafkaTemplate.send("product.created", event.productId().toString(), event);
        log.info("상품 등록 이벤트 발행 완료: productId={}", event.productId());

        // 7. 결과 반환 (옵션 리스트 포함)
        List<ProductResult.OptionResult> optionResults = savedOptions.stream()
            .map(opt -> new ProductResult.OptionResult(opt.getId(), opt.getName()))
            .toList();

        return new ProductResult(product.getId(), optionResults);
    }
}
