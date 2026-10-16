package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.usage.ApiUsageRecord;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real migrations, writes rows with the same upsert the AI service uses, and reads them
 * back through the repository's aggregation.
 */
@Testcontainers
class ApiUsageRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static final String UPSERT_USAGE_SQL = """
            INSERT INTO api_usage_daily
                (usage_date, provider, operation, call_count, quota_units, input_tokens, output_tokens)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (usage_date, provider, operation) DO UPDATE SET
                call_count = api_usage_daily.call_count + EXCLUDED.call_count,
                quota_units = api_usage_daily.quota_units + EXCLUDED.quota_units,
                input_tokens = api_usage_daily.input_tokens + EXCLUDED.input_tokens,
                output_tokens = api_usage_daily.output_tokens + EXCLUDED.output_tokens,
                updated_at = now()
            """;
    private static final String CHAT_COMPLETION = "chat_completion";
    private static final String EMBEDDINGS = "embeddings";
    private static final String VIDEOS_LIST = "videos.list";
    private static final String PLAYLIST_ITEMS_LIST = "playlistItems.list";
    private static final LocalDate MONTH_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 15);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate PREVIOUS_MONTH = LocalDate.of(2026, 9, 30);
    private static final long SINGLE_CALL = 1;
    private static final long NO_UNITS = 0;
    private static final long NO_TOKENS = 0;
    private static final long ONE_UNIT = 1;

    private static JdbcTemplate jdbcTemplate;
    private static ApiUsageRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        repository = new ApiUsageRepository(jdbcTemplate);
    }

    @BeforeEach
    void clearUsage() {
        jdbcTemplate.update("DELETE FROM api_usage_daily");
    }

    private static void recordCall(LocalDate usageDate, String provider, String operation, long quotaUnits,
                                   long inputTokens, long outputTokens) {
        jdbcTemplate.update(UPSERT_USAGE_SQL, usageDate, provider, operation, SINGLE_CALL, quotaUnits,
                inputTokens, outputTokens);
    }

    @Test
    void repeatedCallsOnOneDayAddUpInOneRow() {
        recordCall(TODAY, UsageProvider.DEEPINFRA.name(), CHAT_COMPLETION, NO_UNITS, 100, 20);
        recordCall(TODAY, UsageProvider.DEEPINFRA.name(), CHAT_COMPLETION, NO_UNITS, 50, 5);

        Integer rowCount = jdbcTemplate.queryForObject("SELECT count(*) FROM api_usage_daily", Integer.class);
        assertThat(rowCount).isOne();
        assertThat(repository.totalsByOperation(TODAY, TODAY)).containsExactly(
                new ApiUsageRecord(UsageProvider.DEEPINFRA, CHAT_COMPLETION, 2, NO_UNITS, 150, 25));
    }

    @Test
    void theUniqueKeyIsTheDayProviderAndOperation() {
        jdbcTemplate.update("INSERT INTO api_usage_daily (usage_date, provider, operation) VALUES (?, ?, ?)",
                TODAY, UsageProvider.OPENAI.name(), EMBEDDINGS);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO api_usage_daily (usage_date, provider, operation) VALUES (?, ?, ?)",
                TODAY, UsageProvider.OPENAI.name(), EMBEDDINGS))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void youtubeUnitsAreSummedAcrossEndpointsForOneQuotaDay() {
        recordCall(TODAY, UsageProvider.YOUTUBE.name(), VIDEOS_LIST, ONE_UNIT, NO_TOKENS, NO_TOKENS);
        recordCall(TODAY, UsageProvider.YOUTUBE.name(), VIDEOS_LIST, ONE_UNIT, NO_TOKENS, NO_TOKENS);
        recordCall(TODAY, UsageProvider.YOUTUBE.name(), PLAYLIST_ITEMS_LIST, ONE_UNIT, NO_TOKENS, NO_TOKENS);
        recordCall(YESTERDAY, UsageProvider.YOUTUBE.name(), VIDEOS_LIST, ONE_UNIT, NO_TOKENS, NO_TOKENS);

        assertThat(repository.quotaUnitsOn(UsageProvider.YOUTUBE, TODAY)).isEqualTo(3);
        assertThat(repository.quotaUnitsOn(UsageProvider.YOUTUBE, LocalDate.of(2026, 10, 14))).isZero();
    }

    @Test
    void aMonthRangeSumsEachOperationAndLeavesOutOtherMonthsAndUnknownProviders() {
        recordCall(PREVIOUS_MONTH, UsageProvider.OPENAI.name(), EMBEDDINGS, NO_UNITS, 1_000, NO_TOKENS);
        recordCall(MONTH_START, UsageProvider.OPENAI.name(), EMBEDDINGS, NO_UNITS, 10, NO_TOKENS);
        recordCall(TODAY, UsageProvider.OPENAI.name(), EMBEDDINGS, NO_UNITS, 20, NO_TOKENS);
        recordCall(TODAY, UsageProvider.OPENAI.name(), CHAT_COMPLETION, NO_UNITS, 300, 40);
        recordCall(TODAY, "RETIRED_PROVIDER", CHAT_COMPLETION, NO_UNITS, 999, 999);

        assertThat(repository.totalsByOperation(MONTH_START, TODAY)).containsExactly(
                new ApiUsageRecord(UsageProvider.OPENAI, CHAT_COMPLETION, 1, NO_UNITS, 300, 40),
                new ApiUsageRecord(UsageProvider.OPENAI, EMBEDDINGS, 2, NO_UNITS, 30, NO_TOKENS));
    }
}
