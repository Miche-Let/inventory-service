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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExhibitionSchedulerService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Spring AOP의 프록시를 타기 위한 자기 자신 주입
    @Lazy
    @Autowired
    private ExhibitionSchedulerService self;

    private static final int CHUNK_SIZE = 100;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Value("${inventory.kafka.topic.status-changed:product.status-changed}")
    private String topicStatusChanged;

    // 외부 진입점의 @Transactional을 제거하여 영속성 컨텍스트 비대화를 막음
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul") // 매 정시(0분 0초)마다 실행
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
            publishStatusChangeEventAfterCommit(product);
        }

        return slice.getNumberOfElements();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processClosingChunk(LocalDateTime now) {
        Slice<Product> slice = productRepository.findProductsToClose(now, PageRequest.of(0, CHUNK_SIZE));

        for (Product product : slice.getContent()) {
            product.changeStatus(ProductStatus.EXPIRED);
            publishStatusChangeEventAfterCommit(product);
        }

        return slice.getNumberOfElements();
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
