package org.dariusturcu.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Probe endpoints on the public port. Liveness only proves the process answers requests, so
 * a dependency outage never gets the container restarted. Readiness checks the database
 * alone, with a short-lived cached result so unauthenticated callers cannot turn it into
 * database load. Nothing on these paths calls another service.
 */
@RestController
@ConditionalOnProperty(name = "management.server.port")
public class PublicHealthController {

    private static final Duration READINESS_CACHE_DURATION = Duration.ofSeconds(5);
    private static final int DATABASE_VALIDATION_TIMEOUT_SECONDS = 2;
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";

    private record CachedReadiness(boolean ready, long expiresAtMillis) {
    }

    private final DataSource dataSource;
    private final Clock clock;
    private volatile CachedReadiness cachedReadiness;

    @Autowired
    public PublicHealthController(DataSource dataSource) {
        this(dataSource, Clock.systemUTC());
    }

    PublicHealthController(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @GetMapping("/actuator/health/liveness")
    public ResponseEntity<Map<String, Object>> liveness() {
        return respond(true);
    }

    @GetMapping({"/actuator/health", "/actuator/health/readiness", "/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return respond(isDatabaseReachable());
    }

    private ResponseEntity<Map<String, Object>> respond(boolean healthy) {
        Map<String, Object> responseBody = Map.of(
                "status", healthy ? STATUS_UP : STATUS_DOWN,
                "groups", List.of("liveness", "readiness")
        );
        return healthy
                ? ResponseEntity.ok(responseBody)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(responseBody);
    }

    private boolean isDatabaseReachable() {
        long nowMillis = clock.millis();
        CachedReadiness snapshot = cachedReadiness;
        if (snapshot != null && nowMillis < snapshot.expiresAtMillis()) {
            return snapshot.ready();
        }
        boolean reachable = pingDatabase();
        cachedReadiness = new CachedReadiness(reachable, nowMillis + READINESS_CACHE_DURATION.toMillis());
        return reachable;
    }

    private boolean pingDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException databaseFailure) {
            return false;
        }
    }
}
