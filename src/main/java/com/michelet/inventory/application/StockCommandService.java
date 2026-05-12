package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.ProductStatusChangedEvent;
import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.application.dto.StockRestoredEvent;
import com.michelet.inventory.domain.exception.StockNotFoundException;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductOption;
import com.michelet.inventory.domain.model.ProductStatus;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import com.michelet.inventory.domain.repository.ProductRepository;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.presentation.dto.ReserveStockRequest;
import com.michelet.inventory.presentation.dto.RestoreStockRequest;
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
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${inventory.kafka.topic.reserved:stock.reserved}")
    private String topicStockReserved;

    @Value("${inventory.kafka.topic.restored:stock.restored}")
    private String topicStockRestored;

    @Value("${inventory.kafka.topic.status-changed:product.status-changed}")
    private String topicStatusChanged;

    // while문, Thread.sleep, TransactionTemplate 모두 제거 및 순수 비즈니스 로직만 남김
    @Transactional
    public void reserveStock(ReserveStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

        // 1. 도메인 로직을 통한 3중 검증 및 차감
        stock.reserve(request.quantity());
        stockRepository.save(stock);

        // 2. 품절 상태 자동 전이 로직
        if (stock.getTotalQuantity() == 0) {
            ProductOption option = productOptionRepository.findById(stock.getOptionId())
                .orElseThrow(() -> new IllegalArgumentException("옵션 정보를 찾을 수 없습니다."));
            Product product = option.getProduct();

            // 이미 품절(SOLDOUT)이거나, 삭제(DELETED)되었거나, 만료(EXPIRED)된 상품은 상태를 변경하지 않음
            if (product.getStatus() != ProductStatus.SOLDOUT
                && product.getStatus() != ProductStatus.DELETED
                && product.getStatus() != ProductStatus.EXPIRED) {

                product.changeStatus(ProductStatus.SOLDOUT);
                productRepository.save(product);

                ProductStatusChangedEvent statusEvent = new ProductStatusChangedEvent(product.getId(),
                    product.getStatus().name());
                publishKafkaEvent(
                    topicStatusChanged,
                    product.getId().toString(),
                    statusEvent,
                    "SOLDOUT 상태 변경"
                );
            }
        }

        // 3. Kafka 이벤트 발행 (DB 커밋 이후에 실행되도록 공통 메서드 분리)
        StockReservedEvent event = new StockReservedEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
        publishKafkaEvent(topicStockReserved, request.optionId().toString(), event, "재고차감");
    }

    @Transactional
    public void restoreStock(RestoreStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

        // 1. 도메인 로직: 재고 복구
        stock.restore(request.quantity());

        // 2. DB 업데이트 (더티 체킹 후 flush)
        stockRepository.save(stock);

        // 2. Kafka 이벤트 발행
        StockRestoredEvent event = new StockRestoredEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
        publishKafkaEvent(topicStockRestored, request.optionId().toString(), event, "재고복구");
    }

    // DB 커밋 완료 후에만 Kafka가 발행되도록 보장하는 공통 메서드
    private void publishKafkaEvent(String topic, String key, Object event, String logPrefix) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendToKafka(topic, key, event, logPrefix);
                }
            });
        } else {
            sendToKafka(topic, key, event, logPrefix);
        }
    }

    private void sendToKafka(String topic, String key, Object event, String logPrefix) {
        kafkaTemplate.send(topic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("{} Kafka 메시지 발행 실패 (데이터 불일치 위험)! key: {}", logPrefix, key, ex);
                } else {
                    long offset =
                        (result != null && result.getRecordMetadata() != null) ? result.getRecordMetadata().offset()
                            : -1;
                    log.info("{} Kafka 메시지 발행 성공! key: {}, offset: {}", logPrefix, key, offset);
                }
            });
    }
}
