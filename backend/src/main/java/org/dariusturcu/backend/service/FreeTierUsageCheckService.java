package org.dariusturcu.backend.service;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
/**
 * Watches the free-tier usage that code can actually query: Sentry's accepted
 * error count and Grafana Cloud's active metrics series. Either half stays
 * silent until its credentials exist, the same degrade pattern EmailService
 * uses for a missing Resend key, so local development and CI never need real
 * vendor tokens. Log and trace gigabyte billing has no stable code-queryable
 * endpoint, so that half stays a Grafana console billing alert instead.
 */
@Slf4j
@Service
public class FreeTierUsageCheckService {

    static final String SENTRY_STATS_PATH = "/api/0/organizations/%s/stats_v2/";
    static final String SENTRY_ERROR_CATEGORY = "error";
    static final String SENTRY_ACCEPTED_OUTCOME = "accepted";
    static final String SENTRY_STATS_PERIOD = "30d";
    static final String GRAFANA_ACTIVE_SERIES_QUERY = "sum(grafanacloud_instance_active_series)";

    private final RestClient restClient;

    private final String sentryApiToken;
    private final String sentryOrgSlug;
    private final long sentryMonthlyErrorQuota;
    private final String grafanaUsageQueryUrl;
    private final String grafanaUsageUsername;
    private final String grafanaUsagePassword;
    private final long grafanaMonthlyActiveSeriesQuota;
    private final double warnFraction;

    @Autowired
    public FreeTierUsageCheckService(
            @Value("${usage-check.sentry.api-token:}") String sentryApiToken,
            @Value("${usage-check.sentry.org-slug:}") String sentryOrgSlug,
            @Value("${usage-check.sentry.monthly-error-quota:5000}") long sentryMonthlyErrorQuota,
            @Value("${usage-check.grafana.query-url:}") String grafanaUsageQueryUrl,
            @Value("${usage-check.grafana.username:}") String grafanaUsageUsername,
            @Value("${usage-check.grafana.password:}") String grafanaUsagePassword,
            @Value("${usage-check.grafana.monthly-active-series-quota:10000}") long grafanaMonthlyActiveSeriesQuota,
            @Value("${usage-check.warn-fraction:0.8}") double warnFraction) {
        this(RestClient.builder().build(), sentryApiToken, sentryOrgSlug, sentryMonthlyErrorQuota,
                grafanaUsageQueryUrl, grafanaUsageUsername, grafanaUsagePassword,
                grafanaMonthlyActiveSeriesQuota, warnFraction);
    }

    FreeTierUsageCheckService(
            RestClient restClient,
            String sentryApiToken,
            String sentryOrgSlug,
            long sentryMonthlyErrorQuota,
            String grafanaUsageQueryUrl,
            String grafanaUsageUsername,
            String grafanaUsagePassword,
            long grafanaMonthlyActiveSeriesQuota,
            double warnFraction) {
        this.restClient = restClient;
        this.sentryApiToken = sentryApiToken;
        this.sentryOrgSlug = sentryOrgSlug;
        this.sentryMonthlyErrorQuota = sentryMonthlyErrorQuota;
        this.grafanaUsageQueryUrl = grafanaUsageQueryUrl;
        this.grafanaUsageUsername = grafanaUsageUsername;
        this.grafanaUsagePassword = grafanaUsagePassword;
        this.grafanaMonthlyActiveSeriesQuota = grafanaMonthlyActiveSeriesQuota;
        this.warnFraction = warnFraction;
    }

    @Scheduled(fixedRateString = "${usage-check.interval-milliseconds:86400000}")
    public void checkUsageLimits() {
        checkSentryErrors();
        checkGrafanaSeries();
    }

    private void checkSentryErrors() {
        if (sentryApiToken.isBlank() || sentryOrgSlug.isBlank()) {
            log.info("Sentry usage check skipped, no API token or org slug configured");
            return;
        }
        try {
            JsonNode stats = restClient.get()
                    .uri("https://sentry.io" + String.format(SENTRY_STATS_PATH, sentryOrgSlug)
                            + "?category=" + SENTRY_ERROR_CATEGORY
                            + "&statsPeriod=" + SENTRY_STATS_PERIOD
                            + "&groupBy=outcome")
                    .header("Authorization", "Bearer " + sentryApiToken)
                    .retrieve()
                    .body(JsonNode.class);
            long acceptedErrors = sumAcceptedOutcomes(stats);
            reportUsage("Sentry errors", acceptedErrors, sentryMonthlyErrorQuota);
        } catch (RuntimeException failure) {
            log.warn("Sentry usage check failed: {}", failure.getMessage());
        }
    }

    private void checkGrafanaSeries() {
        if (grafanaUsageQueryUrl.isBlank() || grafanaUsageUsername.isBlank() || grafanaUsagePassword.isBlank()) {
            log.info("Grafana usage check skipped, no usage query credentials configured");
            return;
        }
        try {
            JsonNode result = restClient.get()
                    .uri(grafanaUsageQueryUrl + "/api/v1/query?query=" + GRAFANA_ACTIVE_SERIES_QUERY)
                    .headers(headers -> headers.setBasicAuth(grafanaUsageUsername, grafanaUsagePassword))
                    .retrieve()
                    .body(JsonNode.class);
            long activeSeries = readActiveSeries(result);
            reportUsage("Grafana active series", activeSeries, grafanaMonthlyActiveSeriesQuota);
        } catch (RuntimeException failure) {
            log.warn("Grafana usage check failed: {}", failure.getMessage());
        }
    }

    private void reportUsage(String signal, long used, long quota) {
        double fraction = quota <= 0 ? 0 : (double) used / quota;
        if (fraction >= warnFraction) {
            log.warn("{} at {} of {} free-tier quota, approaching the limit", signal, used, quota);
        } else {
            log.info("{} at {} of {} free-tier quota", signal, used, quota);
        }
    }

    static long sumAcceptedOutcomes(JsonNode stats) {
        long acceptedTotal = 0;
        JsonNode groups = stats == null ? null : stats.get("groups");
        if (groups == null || !groups.isArray()) {
            return acceptedTotal;
        }
        for (JsonNode group : groups) {
            JsonNode outcome = group.path("by").path("outcome");
            if (!SENTRY_ACCEPTED_OUTCOME.equals(outcome.asText())) {
                continue;
            }
            JsonNode totals = group.path("totals");
            for (Map.Entry<String, JsonNode> total : totals.properties()) {
                acceptedTotal += total.getValue().asLong();
            }
        }
        return acceptedTotal;
    }

    static long readActiveSeries(JsonNode result) {
        try {
            return result.path("data").path("result").get(0).path("value").get(1).asLong();
        } catch (RuntimeException failure) {
            return 0;
        }
    }
}
