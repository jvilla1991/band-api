package com.villxin.bandapi.config;

import com.villxin.bandapi.security.JwtAuthFilter;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // App Runner polls this unauthenticated; must be public or the
                        // health check fails and the service never reaches RUNNING.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/subscribe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/posts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/shop/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/shop/checkout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/shop/webhook").permitAll()
                        .requestMatchers("/api/shop/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/shop/sync").hasRole("ADMIN")
                        // Site controls: admin subtree first — it must win over the
                        // public GET matcher below, which would otherwise also match it.
                        .requestMatchers("/api/site/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/site/**").permitAll()
                        // YourArea community — magic-link auth is public
                        .requestMatchers(HttpMethod.POST,
                                "/api/community/auth/signup",
                                "/api/community/auth/login",
                                "/api/community/auth/verify").permitAll()
                        // Public reads: boards/threads, bulletins, profiles + walls.
                        // Everything else under /api/community (writes, DMs, /auth/me)
                        // falls through to authenticated().
                        .requestMatchers(HttpMethod.GET,
                                "/api/community/boards/**",
                                "/api/community/threads/**",
                                "/api/community/bulletins",
                                "/api/community/profiles/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
