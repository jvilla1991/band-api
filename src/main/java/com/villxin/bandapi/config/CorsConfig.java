package com.villxin.bandapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins:}") String[] allowedOrigins) {
        // Fail closed: drop blank entries so an unset CORS_ORIGINS yields NO allowed origins
        // (instead of the old "*" wildcard). The local profile supplies http://localhost:5173.
        this.allowedOrigins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    // Exposed as a CorsConfigurationSource bean so Spring Security's CorsFilter
    // (enabled via http.cors() in SecurityConfig) answers preflights BEFORE the
    // authorization rules run. MVC-level CORS alone is too late: the security
    // chain rejects the anonymous OPTIONS preflight with 403 first.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
