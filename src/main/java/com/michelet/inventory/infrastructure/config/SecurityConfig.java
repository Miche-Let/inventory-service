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
                .anyRequest().permitAll() // 일단 모든 요청을 허용 (헤더 검증은 서비스 로직에서 수행)
            );

        return http.build();
    }
}
