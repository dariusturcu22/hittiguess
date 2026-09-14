package org.dariusturcu.backend.analytics;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// Wires the separate append-heavy Postgres instance for usage/event data (story 33): its
// own connection pool, its own Flyway migration location and schema-history table, kept
// fully independent of the transactional datasource and its own V1-V10 migration history.
@Configuration
public class AnalyticsDataSourceConfig {

    private static final String ANALYTICS_MIGRATION_LOCATION = "classpath:db/analytics-migration";

    @Bean(defaultCandidate = false)
    @ConfigurationProperties("analytics.datasource")
    public DataSourceProperties analyticsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(defaultCandidate = false)
    public DataSource analyticsDataSource(
            @Qualifier("analyticsDataSourceProperties") DataSourceProperties analyticsDataSourceProperties
    ) {
        return analyticsDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean
    public Flyway analyticsFlyway(@Qualifier("analyticsDataSource") DataSource analyticsDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(analyticsDataSource)
                .locations(ANALYTICS_MIGRATION_LOCATION)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    @DependsOn("analyticsFlyway")
    public JdbcTemplate analyticsJdbcTemplate(@Qualifier("analyticsDataSource") DataSource analyticsDataSource) {
        return new JdbcTemplate(analyticsDataSource);
    }
}
