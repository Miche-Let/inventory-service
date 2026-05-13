package com.michelet.inventory.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    /**
     * 카프카 컨슈머 에러 핸들러 설정 - 예외 발생 시 1초 간격으로 3번 재시도 후 DLT 토픽으로 전송
     */
    @Value("${inventory.kafka.consumer.retry.interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${inventory.kafka.consumer.retry.max-attempts:3}")
    private long retryMaxAttempts;

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // 1. 에러가 난 메시지를 DLT(Dead Letter Topic, 예: stock.restored.DLT)로 보내는 역할
        // 파티션 충돌 방지 및 추적 로그 삽입
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (cr, e) -> {
                log.error("[Inventory 장애 감지] 에러 핸들러 작동! DLT 토픽으로 이동. 대상: {}", cr.topic() + ".DLT");
                // 원본 파티션을 고집하지 않고 가용한 파티션에 안전하게 넣도록 강제 (-1)
                return new TopicPartition(cr.topic() + ".DLT", -1);
            }
        );

        // 2. 기본 재시도 정책: 1초(1000ms) 간격으로 최대 3번 재시도
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
            new FixedBackOff(retryIntervalMs, retryMaxAttempts));

        // 3. 특정 예외(형식이 아예 틀린 경우)는 재시도해봤자 의미 없으므로 즉시 DLT로 직행
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
        ConsumerFactory<Object, Object> consumerFactory,
        DefaultErrorHandler errorHandler // 위에서 만든 에러 핸들러 주입
    ) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltListenerContainerFactory(
        ConsumerFactory<Object, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        Map<String, Object> props = new HashMap<>(consumerFactory.getConfigurationProperties());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.remove("spring.deserializer.value.delegate.class");

        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        return factory;
    }
}
