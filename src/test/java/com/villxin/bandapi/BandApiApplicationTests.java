package com.villxin.bandapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real Postgres (Testcontainers), so Flyway applies the
 * actual Postgres migrations exactly as it would in prod — and {@code ddl-auto=validate} then checks
 * the JPA entities against that schema. {@code @ServiceConnection} wires the datasource to the
 * container automatically. Requires Docker to be running (image matches the shared RDS — Postgres 16).
 */
@SpringBootTest
@Testcontainers
class BandApiApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
    }
}
