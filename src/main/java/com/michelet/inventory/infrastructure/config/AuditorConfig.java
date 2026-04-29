package com.michelet.inventory.infrastructure.config;

import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class AuditorConfig {

    @Bean(name = "auditorAware")
    @Profile("!test")
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
            .map(auth -> {
                // 실제 운영시에는 JWT에서 추출한 user_id를 사용!
                try {
                    return UUID.fromString(auth.getName());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            // TODO: User 서비스 및 JWT 연동 완료 시, 아래 임시 UUID 반환 코드를 삭제하고 Optional.empty()가 반환되도록 수정해야 함.
            .or(() -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Bean(name = "auditorAware")
    @Profile("test")
    public AuditorAware<UUID> testAuditorAware() {
        return () -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
