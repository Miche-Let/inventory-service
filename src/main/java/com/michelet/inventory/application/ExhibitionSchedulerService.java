package com.michelet.inventory.application;

import com.michelet.inventory.application.dto.ProductStatusChangedEvent;
import com.michelet.inventory.domain.model.Product;
import com.michelet.inventory.domain.model.ProductStatus;
import com.michelet.inventory.domain.repository.ProductRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExhibitionSchedulerService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int CHUNK_SIZE = 100;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Value("${inventory.kafka.topic.status-changed:product.status-changed}")
    private String topicStatusChanged;

    @Transactional
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul") // 매 정시(0분 0초)마다 실행
    public void updateExhibitionStatus() {
        log.info("정시 전시 상태 변경 스케줄러 시작...");
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);

        processOpeningProducts(now);
        processClosingProducts(now);

        log.info("정시 전시 상태 변경 스케줄러 완료.");
    }

    private void processOpeningProducts(LocalDateTime now) {
        Slice<Product> slice;
        do {
            // 변경된 데이터는 다음 조회(status='HIDDEN')에서 제외되므로 페이지넘버를 0으로 고정함
            slice = productRepository.findProductsToOpen(now, PageRequest.of(0, CHUNK_SIZE));
            for (Product product : slice.getContent()) {
                product.changeStatus(ProductStatus.ACTIVE);
                publishStatusChangeEventAfterCommit(product); // 트랜잭션 커밋 후 발행
            }
        } while (slice.hasNext());
    }

    private void processClosingProducts(LocalDateTime now) {
        Slice<Product> slice;
        do {
            // 변경된 데이터는 다음 조회(status='ACTIVE')에서 제외되므로 페이지넘버를 0으로 고정함
            slice = productRepository.findProductsToClose(now, PageRequest.of(0, CHUNK_SIZE));
            for (Product product : slice.getContent()) {
                product.changeStatus(ProductStatus.HIDDEN);
                publishStatusChangeEventAfterCommit(product); // 트랜잭션 커밋 후 발행
            }
        } while (slice.hasNext());
    }

    // DB 커밋 성공 시에만 카프카로 이벤트 발행 보장
    private void publishStatusChangeEventAfterCommit(Product product) {
        ProductStatusChangedEvent event = new ProductStatusChangedEvent(product.getId(), product.getStatus().name());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendKafkaEvent(product, event);
                }
            });
        } else {
            sendKafkaEvent(product, event);
        }
    }

    private void sendKafkaEvent(Product product, ProductStatusChangedEvent event) {
        kafkaTemplate.send(topicStatusChanged, product.getId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("상품 상태 변경 카프카 이벤트 발행 실패: productId={}", product.getId(), ex);
                }
            });
    }
}
