package com.michelet.inventory.infrastructure.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

@Configuration
@EntityScan(basePackages = {"com.michelet.inventory", "com.michelet.common"})
public class JpaConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        // 지금은 테스트 단계이므로 임시 UUID를 반환함
        //TODO 나중에 SecurityContext 등에서 실제 사용자 ID를 꺼내오도록 수정해야함
        return () -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
