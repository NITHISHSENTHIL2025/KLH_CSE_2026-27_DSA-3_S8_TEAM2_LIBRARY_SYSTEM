package com.intellilib.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
@EnableScheduling
public class IntelliLibServerApplication {
    public static void main(String[] args) {
        configureDatabaseFromEnvironment();
        SpringApplication.run(IntelliLibServerApplication.class, args);
    }

    /**
     * Neon commonly provides postgresql://user:password@host/database?sslmode=require.
     * Spring/Hikari expects a JDBC URL. Convert it before Spring creates the datasource.
     * A normal jdbc:postgresql:// URL is also accepted unchanged.
     */
    private static void configureDatabaseFromEnvironment() {
        String raw = System.getenv("DATABASE_URL");
        if (raw == null || raw.isBlank()) return;
        if (raw.startsWith("jdbc:")) {
            System.setProperty("spring.datasource.url", raw);
            return;
        }
        try {
            URI uri = URI.create(raw);
            if (!"postgres".equalsIgnoreCase(uri.getScheme()) && !"postgresql".equalsIgnoreCase(uri.getScheme())) return;
            String[] userInfo = uri.getRawUserInfo() == null ? new String[0] : uri.getRawUserInfo().split(":", 2);
            if (userInfo.length > 0) System.setProperty("spring.datasource.username", decode(userInfo[0]));
            if (userInfo.length > 1) System.setProperty("spring.datasource.password", decode(userInfo[1]));
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getRawQuery();
            String jdbc = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + path
                    + (query == null || query.isBlank() ? "" : "?" + query);
            System.setProperty("spring.datasource.url", jdbc);
        } catch (Exception e) {
            throw new IllegalStateException("DATABASE_URL is not a valid PostgreSQL connection URL", e);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
