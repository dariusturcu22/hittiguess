package org.dariusturcu.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicHealthControllerTest {

    private static final Duration PAST_THE_CACHE_DURATION = Duration.ofSeconds(6);
    private static final Duration WITHIN_THE_CACHE_DURATION = Duration.ofSeconds(1);

    private static final class AdjustableClock extends Clock {
        private Instant currentInstant = Instant.parse("2030-01-01T00:00:00Z");

        void advance(Duration elapsed) {
            currentInstant = currentInstant.plus(elapsed);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }

    private static DataSource dataSourceWithValidity(boolean valid) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(valid);
        return dataSource;
    }

    @Test
    void livenessIsUpWithoutTouchingTheDatabase() {
        DataSource dataSource = mock(DataSource.class);
        PublicHealthController controller = new PublicHealthController(dataSource, new AdjustableClock());

        ResponseEntity<Map<String, Object>> response = controller.liveness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("groups")).isEqualTo(List.of("liveness", "readiness"));
        verifyNoInteractions(dataSource);
    }

    @Test
    void livenessStaysUpWhenTheDatabaseIsDown() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));
        PublicHealthController controller = new PublicHealthController(dataSource, new AdjustableClock());

        assertThat(controller.liveness().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void readinessIsUpWhenTheDatabaseIsReachable() throws SQLException {
        PublicHealthController controller =
                new PublicHealthController(dataSourceWithValidity(true), new AdjustableClock());

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("groups")).isEqualTo(List.of("liveness", "readiness"));
    }

    @Test
    void readinessIsUnavailableWhenTheDatabaseConnectionFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));
        PublicHealthController controller = new PublicHealthController(dataSource, new AdjustableClock());

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("DOWN");
    }

    @Test
    void readinessIsUnavailableWhenTheConnectionIsNotValid() throws SQLException {
        PublicHealthController controller =
                new PublicHealthController(dataSourceWithValidity(false), new AdjustableClock());

        assertThat(controller.health().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readinessResultIsCachedForAFewSeconds() throws SQLException {
        DataSource dataSource = dataSourceWithValidity(true);
        AdjustableClock clock = new AdjustableClock();
        PublicHealthController controller = new PublicHealthController(dataSource, clock);

        controller.health();
        clock.advance(WITHIN_THE_CACHE_DURATION);
        controller.health();

        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void readinessChecksTheDatabaseAgainOnceTheCacheExpires() throws SQLException {
        DataSource dataSource = dataSourceWithValidity(true);
        AdjustableClock clock = new AdjustableClock();
        PublicHealthController controller = new PublicHealthController(dataSource, clock);

        controller.health();
        clock.advance(PAST_THE_CACHE_DURATION);
        controller.health();

        verify(dataSource, times(2)).getConnection();
    }
}
