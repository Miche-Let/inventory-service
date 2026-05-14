package com.michelet.inventory.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    /**
     * Inventory 서비스의 주요 도메인 로직(재고 복구 등) 실패 시 격리된 메시지를 수신 - 3회 재시도 후에도 실패한 '독성 메시지'를 분석하기 위한 전용 컨슈머
     */
    @KafkaListener(
        topics = {
            "${inventory.kafka.topic.restore-request:order.stock-restore.requested}.DLT",
            "${inventory.kafka.topic.stock-restored:stock.restored}.DLT",
            "${inventory.kafka.topic.stock-reserved:stock.reserved}.DLT"
        },
        groupId = "${spring.kafka.consumer.group-id:inventory-service-consumer}-dlt",
        containerFactory = "dltListenerContainerFactory" // String 전용 팩토리 사용
    )
    public void consumeInventoryDLT(ConsumerRecord<String, String> record) {
        String originalTopic = extractHeaderAsString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
        String exceptionMessage = extractHeaderAsString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String stackTrace = extractHeaderAsString(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE);

        log.error("""
                ================================================================================
                [CRITICAL ALERT] 인벤토리 DLT 에러 메시지 격리 수신 (최종 실패)
                원본 토픽 : {}
                에러 원인 : {}
                원본 데이터 : {}
                상세 트레이스 : 
                {}
                ================================================================================""",
            originalTopic,
            exceptionMessage,
            record.value() != null ? record.value() : "데이터 없음",
            stackTrace
        );
    }

    private String extractHeaderAsString(ConsumerRecord<String, String> record, String headerKey) {
        Header header = record.headers().lastHeader(headerKey);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "알 수 없음";
    }
}
