package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.ProductStatusChangedEvent;
import com.michelet.inventory.application.dto.ReserveStockRequest;
import com.michelet.inventory.application.dto.RestoreStockRequest;
import com.michelet.inventory.application.dto.StockReservedEvent;
import com.michelet.inventory.application.dto.StockRestoredEvent;
import com.michelet.inventory.domain.exception.StockNotFoundException;
import com.michelet.inventory.domain.model.ProcessedEvent;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductOption;
import com.michelet.inventory.domain.model.ProductStatus;
import com.michelet.inventory.domain.model.Stock;
import com.michelet.inventory.domain.repository.ProcessedEventRepository;
import com.michelet.inventory.domain.repository.ProductOptionRepository;
import com.michelet.inventory.domain.repository.ProductRepository;
import com.michelet.inventory.domain.repository.StockRepository;
import com.michelet.inventory.infrastructure.messaging.dto.OrderApprovedEvent;
import com.michelet.inventory.infrastructure.messaging.dto.OrderCreatedMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductRepository productRepository;
    private final ProcessedEventRepository processedEventRepository;

    // OutboxHelper 주입 (KafkaTemplate 대체)
    private final InventoryOutboxHelper outboxHelper;

    @Transactional
    public void reserveStock(ReserveStockRequest request) {
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

        // 1. 도메인 로직 검증 및 차감
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
                // 카프카 직접 발송 대신 Outbox에 적재
                outboxHelper.append("PRODUCT", product.getId().toString(), "PRODUCT_STATUS_CHANGED", statusEvent);
            }
        }

        // 3. 재고 차감 이벤트 Outbox 적재
        StockReservedEvent event = new StockReservedEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
        outboxHelper.append("STOCK", request.optionId().toString(), "STOCK_RESERVED", event);
    }

    @Transactional
    public void restoreStock(RestoreStockRequest request) {
        // 1. 멱등성 검증 (Redisson Lock 내부이므로 동시성 중복 방어됨)
        if (processedEventRepository.existsById(request.eventId())) {
            log.warn("[Idempotency] 이미 처리된 카프카 메시지입니다. 중복 복구를 방지하고 반환합니다. eventId={}", request.eventId());
            return; // 중복 메시지는 로직을 건너뛰고 정상 완료 처리
        }

        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

        // 2. 도메인 로직: 재고 복구
        stock.restore(request.quantity());

        // 3. DB 업데이트
        stockRepository.save(stock);

        // 4. 처리 완료 기록 (멱등키 저장)
        processedEventRepository.save(new ProcessedEvent(request.eventId()));

        // 5. 재고 복구 결과를 Outbox에 적재하여 카탈로그로 발송
        StockRestoredEvent event = new StockRestoredEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
        outboxHelper.append("STOCK", request.optionId().toString(), "STOCK_RESTORED", event);
    }

    // 다중 재고 비동기 처리 트랜잭션
    @Transactional
    public void processOrderCreation(OrderCreatedMessage msg) {
        if (processedEventRepository.existsById(msg.eventId())) {
            log.warn("[Idempotency] 이미 처리된 주문 메시지입니다. reservationId={}", msg.reservationId());
            return;
        }

        List<Stock> modifiedStocks = new ArrayList<>();

        // 모든 아이템 재고 차감 시도 (실패 시 BusinessException 발생하여 Facade -> Consumer 로 롤백됨)
        for (var item : msg.items()) {
            Stock stock = stockRepository.findById(item.optionId())
                .orElseThrow(StockNotFoundException::new);

            stock.reserve(item.quantity());
            modifiedStocks.add(stock);

            // 품절 처리 이벤트 발송 준비
            if (stock.getTotalQuantity() == 0) {
                ProductOption option = productOptionRepository.findById(stock.getOptionId()).orElseThrow();
                Product product = option.getProduct();
                if (product.getStatus() != ProductStatus.SOLDOUT && product.getStatus() != ProductStatus.DELETED
                    && product.getStatus() != ProductStatus.EXPIRED) {
                    product.changeStatus(ProductStatus.SOLDOUT);
                    productRepository.save(product);
                    outboxHelper.append("PRODUCT", product.getId().toString(), "PRODUCT_STATUS_CHANGED",
                        new ProductStatusChangedEvent(product.getId(), product.getStatus().name()));
                }
            }
        }

        stockRepository.saveAll(modifiedStocks);
        processedEventRepository.save(new ProcessedEvent(msg.eventId()));

        // 모두 성공 시 승인 이벤트 적재
        outboxHelper.append("ORDER", msg.reservationId().toString(), "ORDER_APPROVED",
            new OrderApprovedEvent(msg.reservationId()));
        log.info("[Inventory Saga] 다중 주문 재고 선점 성공 -> 승인 이벤트 적재 완료: {}", msg.reservationId());
    }
}
