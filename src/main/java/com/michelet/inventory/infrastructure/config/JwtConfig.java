package com.michelet.inventory.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    // JWT_SECRET이 없으면 앱 구동을 막기 위한 검증
    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET 환경 변수가 설정되지 않았습니다. 보안을 위해 애플리케이션을 시작할 수 없습니다.");
        }
    }
}
