package com.michelet.inventory.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // CSRF 보호 비활성화 (API 서버이므로)
            .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 폼 비활성화
            .httpBasic(AbstractHttpConfigurer::disable) // Basic 인증 비활성화
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/admin/products/health", "/internal/**").permitAll()
                // 추후 Swagger나 Public 엔드포인트가 생기면 여기에 permitAll() 로 추가
                .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요 (새로운 API 추가 시 자동 적용)
            );

        return http.build();
    }
}
