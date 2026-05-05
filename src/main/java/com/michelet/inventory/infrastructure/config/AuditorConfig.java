package com.michelet.inventory.infrastructure.config;

import com.michelet.common.auth.webmvc.context.UserContextHolder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;

@Configuration
public class AuditorConfig {

    @Bean(name = "auditorAware")
    @Profile("!test")
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            try {
                if (UserContextHolder.get() != null && UserContextHolder.get().userId() != null) {
                    String userIdStr = UserContextHolder.get().userId();
                    return Optional.of(UUID.nameUUIDFromBytes(userIdStr.getBytes()));
                }
            } catch (Exception e) {
                // 에러 시 무시하고 시스템 계정으로 처리?
            }
            return Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        };
    }

    @Bean(name = "auditorAware")
    @Profile("test")
    public AuditorAware<UUID> testAuditorAware() {
        return () -> Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }
}
