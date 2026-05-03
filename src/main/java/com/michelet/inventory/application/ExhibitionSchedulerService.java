package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.ProductStatusChangedEvent;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductStatus;
import com.michelet.inventory.domain.repository.ProductRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExhibitionSchedulerService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int CHUNK_SIZE = 100;

    @Value("${inventory.kafka.topic.status-changed:product.status-changed}")
    private String topicStatusChanged;

    @Transactional
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul") // 매 정시(0분 0초)마다 실행
    public void updateExhibitionStatus() {
        log.info("정시 전시 상태 변경 스케줄러 시작...");
        LocalDateTime now = LocalDateTime.now();

        processOpeningProducts(now);
        processClosingProducts(now);

        log.info("정시 전시 상태 변경 스케줄러 완료.");
    }

    private void processOpeningProducts(LocalDateTime now) {
        int page = 0;
        Slice<Product> slice;
        do {
            slice = productRepository.findProductsToOpen(now, PageRequest.of(page, CHUNK_SIZE));
            for (Product product : slice.getContent()) {
                product.changeStatus(ProductStatus.ACTIVE);
                publishStatusChangeEvent(product);
            }
            page++;
        } while (slice.hasNext());
    }

    private void processClosingProducts(LocalDateTime now) {
        int page = 0;
        Slice<Product> slice;
        do {
            slice = productRepository.findProductsToClose(now, PageRequest.of(page, CHUNK_SIZE));
            for (Product product : slice.getContent()) {
                product.changeStatus(ProductStatus.HIDDEN);
                publishStatusChangeEvent(product);
            }
            page++;
        } while (slice.hasNext());
    }

    private void publishStatusChangeEvent(Product product) {
        ProductStatusChangedEvent event = new ProductStatusChangedEvent(product.getId(), product.getStatus().name());
        kafkaTemplate.send(topicStatusChanged, product.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("상품 상태 변경 카프카 이벤트 발행 실패: productId={}", product.getId(), ex);
                }
            });
    }
}
