package com.michelet.inventory.infrastructure.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EntityScan(basePackages = {"com.michelet.inventory", "com.michelet.common"})
public class JpaConfig {

    @Bean
    @Profile("!test")
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .map(auth -> {
                // 현재는 테스트를 위해 랜덤 UUID를 반환하거나, auth.getName()을 UUID로 변환
                // 실제 운영시에는 JWT에서 추출한 user_id를 사용!
                try {
                    return UUID.fromString(auth.getName());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            });
    }

    @Bean(name = "auditorAware")
    @Profile("test")
    public AuditorAware<UUID> testAuditorAware() {
        return () -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
