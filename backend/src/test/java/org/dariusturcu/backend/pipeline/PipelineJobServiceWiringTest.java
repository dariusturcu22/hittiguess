package org.dariusturcu.backend.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the engine beans the way the application context does, through their annotated
 * constructors, without connecting to a database.
 */
class PipelineJobServiceWiringTest {

    private static final String UNUSED_DATABASE_URL = "jdbc:postgresql://localhost:1/unused";

    @Test
    void theRepositoryAndServiceWireFromTheCoreJdbcTemplateAndTheTransactionManager() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            DataSource dataSource = new DriverManagerDataSource(UNUSED_DATABASE_URL);
            context.registerBean("coreJdbcTemplate", JdbcTemplate.class, () -> new JdbcTemplate(dataSource));
            context.registerBean(PlatformTransactionManager.class, () -> new JdbcTransactionManager(dataSource));
            context.register(PipelineJobRepository.class, PipelineJobService.class);
            context.refresh();

            assertThat(context.getBean(PipelineJobService.class)).isNotNull();
        }
    }
}
