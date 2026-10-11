package com.intellilib.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class AppConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AppSettings appSettings(
            @Value("${app.card-pepper}") String pepper,
            @Value("${app.demo-data:false}") boolean demoData,
            @Value("${app.bootstrap-admin-email}") String adminEmail,
            @Value("${app.bootstrap-admin-password}") String adminPassword,
            @Value("${app.default-due-days:14}") int dueDays) {
        return new AppSettings(pepper, demoData, adminEmail, adminPassword, dueDays);
    }

    public record AppSettings(String cardPepper, boolean demoData, String adminEmail, String adminPassword, int defaultDueDays) {}
}
