package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.usage.ApiUsageRecord;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the daily usage rows the AI service writes. Rows for a provider core does not know are
 * left out rather than failing the whole read.
 */
@Repository
public class ApiUsageRepository {

    private static final Set<String> KNOWN_PROVIDERS = Arrays.stream(UsageProvider.values())
            .map(UsageProvider::name)
            .collect(Collectors.toUnmodifiableSet());

    private final JdbcTemplate core;

    public ApiUsageRepository(@Qualifier("coreJdbcTemplate") JdbcTemplate core) {
        this.core = core;
    }

    public long quotaUnitsOn(UsageProvider provider, LocalDate usageDate) {
        Long quotaUnits = core.queryForObject("""
                SELECT COALESCE(SUM(quota_units), 0) FROM api_usage_daily
                WHERE provider = ? AND usage_date = ?
                """, Long.class, provider.name(), usageDate);
        return quotaUnits == null ? 0 : quotaUnits;
    }

    public List<ApiUsageRecord> totalsByOperation(LocalDate firstDay, LocalDate lastDay) {
        List<ApiUsageRecord> totals = new ArrayList<>();
        core.query("""
                SELECT provider, operation, SUM(call_count) AS call_count, SUM(quota_units) AS quota_units,
                    SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens
                FROM api_usage_daily
                WHERE usage_date BETWEEN ? AND ?
                GROUP BY provider, operation
                ORDER BY provider, operation
                """, resultSet -> {
            String provider = resultSet.getString("provider");
            if (KNOWN_PROVIDERS.contains(provider)) {
                totals.add(new ApiUsageRecord(UsageProvider.valueOf(provider), resultSet.getString("operation"),
                        resultSet.getLong("call_count"), resultSet.getLong("quota_units"),
                        resultSet.getLong("input_tokens"), resultSet.getLong("output_tokens")));
            }
        }, firstDay, lastDay);
        return totals;
    }
}
