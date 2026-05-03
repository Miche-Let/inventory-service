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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Value("${inventory.kafka.topic.product-created:product.created}")
    private String topicProductCreated;

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

        // 리스트 크기 불일치 시 명확한 에러 발생
        if (savedOptions.size() != requestOptions.size()) {
            throw new IllegalStateException(
                String.format("저장된 옵션 개수(%d)와 요청된 옵션 개수(%d)가 일치하지 않습니다.",
                    savedOptions.size(), requestOptions.size())
            );
        }

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
            product.getBasePrice(),
            command.attributes(),
            command.exhibition().startAt(),
            command.exhibition().endAt(),
            optionEventDtos
        );

        // 6. DB 커밋 완료 후에만 카프카 메시지 전송 (정합성 보장) 및 Callback 확인
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendKafkaMessageWithCallback(event);
                }
            });
        } else {
            // 단위 테스트 등 트랜잭션 동기화가 활성화되지 않은 환경을 위한 폴백
            sendKafkaMessageWithCallback(event);
        }

        // 7. 결과 반환 (옵션 리스트 포함)
        List<ProductResult.OptionResult> optionResults = savedOptions.stream()
            .map(opt -> new ProductResult.OptionResult(opt.getId(), opt.getName()))
            .toList();

        return new ProductResult(product.getId(), optionResults);
    }

    // 카프카 전송 및 콜백 확인용 내부 메서드
    private void sendKafkaMessageWithCallback(ProductCreatedEvent event) {
        log.info("DB 커밋 완료. 상품 등록 이벤트 발행 요청: productId={}", event.productId());
        kafkaTemplate.send(topicProductCreated, event.productId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("상품 등록 이벤트 발행 실제 성공: productId={}, offset={}",
                        event.productId(), result.getRecordMetadata().offset());
                } else {
                    log.error("상품 등록 이벤트 발행 실패 (Dead Letter Queue 처리 필요): productId={}",
                        event.productId(), ex);
                }
            });
    }
}
