package com.fertilizer.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String WEBHOOK_PATH = "/webhooks/facebook";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Webhook không có session/CSRF token; được bảo vệ bằng chữ ký của Meta
            .csrf(csrf -> csrf.ignoringRequestMatchers(WEBHOOK_PATH))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(WEBHOOK_PATH).permitAll()
                .anyRequest().authenticated())
            // Tạm giữ Basic; A-03 sẽ thay bằng đăng nhập theo session
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}