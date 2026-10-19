package org.dariusturcu.backend.migration;

import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every other test in this package runs Flyway by hand through its Java API,
 * to control exactly when migration happens relative to inserting legacy
 * data. That never exercises Spring Boot's own autoconfigured Flyway bean,
 * the thing that actually runs on a real `mvnw spring-boot:run` startup with
 * no test overriding anything. This test leaves Flyway on and lets Spring
 * Boot run it against a completely fresh database, the same way starting the
 * real application does: if the migrations don't apply on their own, schema
 * validation fails and the context never comes up.
 */
@Testcontainers
@SpringBootTest(classes = FlywayAutoConfigurationRunsOnStartupTest.JpaTestConfig.class)
class FlywayAutoConfigurationRunsOnStartupTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class JpaTestConfig {
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SongRepository songRepository;

    @Test
    void migratesAFreshDatabaseAndStartsSuccessfully() {
        // Reaching this point at all means the context started, which means Spring Boot's
        // own Flyway bean ran V1-V4 and Hibernate's schema validation passed against the
        // result; querying confirms the table it validated is actually reachable too.
        assertThat(songRepository.findAll()).isEmpty();
    }
}
