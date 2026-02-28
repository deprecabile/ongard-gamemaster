package com.ongard.game.chat;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
public abstract class TestcontainersConfiguration {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1-alpine")
            .withDatabaseName("db-game")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("tc-init-schema.sql");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgres.getJdbcUrl();
        // App datasource: ms_usr (come in produzione)
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "ms_usr");
        registry.add("spring.datasource.password", () -> "ms_usr_password");
        // Liquibase: postgres superuser (come in docker-compose)
        registry.add("spring.liquibase.url", () -> jdbcUrl);
        registry.add("spring.liquibase.user", () -> "postgres");
        registry.add("spring.liquibase.password", () -> "postgres");
    }
}
