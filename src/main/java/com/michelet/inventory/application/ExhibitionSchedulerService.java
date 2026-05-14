package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.ProductStatusChangedEvent;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductStatus;
import com.michelet.inventory.domain.repository.ProductRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExhibitionSchedulerService {

    private final ProductRepository productRepository;
    private final InventoryOutboxHelper outboxHelper;

    // Spring AOP의 프록시를 타기 위한 자기 자신 주입
    @Lazy
    @Autowired
    private ExhibitionSchedulerService self;

    private static final int CHUNK_SIZE = 100;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")  // 매 정시(0분 0초)마다 실행
    public void updateExhibitionStatus() {
        log.info("정시 전시 상태 변경 스케줄러 시작...");
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);

        processOpeningProducts(now);
        processClosingProducts(now);

        log.info("정시 전시 상태 변경 스케줄러 완료.");
    }

    private void processOpeningProducts(LocalDateTime now) {
        while (true) {
            // 프록시 객체를 통해 호출하여 매 루프(Chunk)마다 독립된 트랜잭션을 연다
            int processed = self.processOpeningChunk(now);
            if (processed == 0) {
                break;
            }
        }
    }

    private void processClosingProducts(LocalDateTime now) {
        while (true) {
            int processed = self.processClosingChunk(now);
            if (processed == 0) {
                break;
            }
        }
    }

    // REQUIRES_NEW: 청크가 끝날 때마다 DB 트랜잭션을 커밋하고 L1 캐시를 비워 OOM을 방지함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processOpeningChunk(LocalDateTime now) {
        Slice<Product> slice = productRepository.findProductsToOpen(now, PageRequest.of(0, CHUNK_SIZE));

        for (Product product : slice.getContent()) {
            product.changeStatus(ProductStatus.ACTIVE);
            publishStatusChangeEvent(product);
        }

        return slice.getNumberOfElements();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processClosingChunk(LocalDateTime now) {
        Slice<Product> slice = productRepository.findProductsToClose(now, PageRequest.of(0, CHUNK_SIZE));

        for (Product product : slice.getContent()) {
            product.changeStatus(ProductStatus.EXPIRED);
            publishStatusChangeEvent(product);
        }

        return slice.getNumberOfElements();
    }

    private void publishStatusChangeEvent(Product product) {
        ProductStatusChangedEvent event = new ProductStatusChangedEvent(product.getId(), product.getStatus().name());
        outboxHelper.append("PRODUCT", product.getId().toString(), "PRODUCT_STATUS_CHANGED", event);
    }
}
