package org.dariusturcu.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class FreeTierUsageCheckServiceTest {

    private static final long ERROR_QUOTA = 5000L;
    private static final long SERIES_QUOTA = 10000L;
    private static final double WARN_FRACTION = 0.8;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private FreeTierUsageCheckService serviceWith(RestClient restClient) {
        return new FreeTierUsageCheckService(
                restClient,
                "sentry-token", "org-slug", ERROR_QUOTA,
                "https://usage.example.com", "usage-user", "usage-password", SERIES_QUOTA,
                WARN_FRACTION);
    }

    @Test
    void sumsAcceptedOutcomesAcrossGroups() throws Exception {
        String statsPayload = """
                {"groups": [
                  {"by": {"outcome": "accepted"}, "totals": {"t1": 30, "t2": 12}},
                  {"by": {"outcome": "rate_limited"}, "totals": {"t1": 7}},
                  {"by": {"outcome": "accepted"}, "totals": {"t1": 3}}
                ]}
                """;

        long acceptedTotal = FreeTierUsageCheckService.sumAcceptedOutcomes(
                objectMapper.readTree(statsPayload));

        assertThat(acceptedTotal).isEqualTo(45);
    }

    @Test
    void readsActiveSeriesFromTheQueryVector() throws Exception {
        String queryPayload = """
                {"status": "success", "data": {"result": [{"metric": {}, "value": [1700000000, "8123"]}]}}
                """;

        long activeSeries = FreeTierUsageCheckService.readActiveSeries(
                objectMapper.readTree(queryPayload));

        assertThat(activeSeries).isEqualTo(8123);
    }

    @Test
    void missingQueryVectorReadsAsZero() throws Exception {
        long activeSeries = FreeTierUsageCheckService.readActiveSeries(
                objectMapper.readTree("{\"status\": \"success\", \"data\": {\"result\": []}}"));

        assertThat(activeSeries).isZero();
    }

    @Test
    void skipsBothHalvesWithoutCredentials() {
        RestClient restClient = RestClient.builder().build();
        FreeTierUsageCheckService usageCheck = new FreeTierUsageCheckService(
                restClient, "", "", ERROR_QUOTA, "", "", "", SERIES_QUOTA, WARN_FRACTION);

        usageCheck.checkUsageLimits();
    }

    @Test
    void warnsWhenSentryErrorsPassTheThreshold() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.createServer(builder);
        server.expect(requestTo(startsWith("https://sentry.io/api/0/organizations/org-slug/stats_v2/")))
                .andExpect(queryParam("category", "error"))
                .andExpect(queryParam("statsPeriod", "30d"))
                .andRespond(withSuccess(
                        "{\"groups\": [{\"by\": {\"outcome\": \"accepted\"}, \"totals\": {\"t1\": 4500}}]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://usage.example.com/api/v1/query")))
                .andExpect(queryParam("query", "sum(grafanacloud_instance_active_series)"))
                .andRespond(withSuccess(
                        "{\"status\": \"success\", \"data\": {\"result\": [{\"metric\": {}, \"value\": [1700000000, \"100\"]}]}}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        serviceWith(builder.build()).checkUsageLimits();

        server.verify();
    }
}
