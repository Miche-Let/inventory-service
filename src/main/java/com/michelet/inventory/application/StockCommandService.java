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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductRepository productRepository;

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
        Stock stock = stockRepository.findById(request.optionId())
            .orElseThrow(StockNotFoundException::new);

        // 1. 도메인 로직: 재고 복구
        stock.restore(request.quantity());

        // 2. DB 업데이트 (더티 체킹 후 flush)
        stockRepository.save(stock);

        // 3. 재고 복구 이벤트를 Outbox에 적재
        StockRestoredEvent event = new StockRestoredEvent(
            stock.getOptionId(),
            stock.getTotalQuantity(),
            stock.getCurrentDailyStock()
        );
        outboxHelper.append("STOCK", request.optionId().toString(), "STOCK_RESTORED", event);
    }
}
