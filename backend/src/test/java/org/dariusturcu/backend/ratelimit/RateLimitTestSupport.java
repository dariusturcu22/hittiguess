package org.dariusturcu.backend.ratelimit;

import org.springframework.aop.framework.AopProxyUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Empties every rate limiter held by a bean so a Spring context shared across test methods
 * does not carry limit state from one test into the next.
 */
public final class RateLimitTestSupport {

    private RateLimitTestSupport() {
    }

    public static void reset(Object owner) {
        Object target = AopProxyUtils.getSingletonTarget(owner);
        clearLimiters(target != null ? target : owner);
    }

    private static void clearLimiters(Object owner) {
        for (Field field : owner.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof RateLimiterRegistry registry) {
                    registry.clear();
                } else if (value instanceof Map<?, ?> map) {
                    map.values().stream()
                            .filter(RateLimiterRegistry.class::isInstance)
                            .forEach(registryValue -> ((RateLimiterRegistry) registryValue).clear());
                }
            } catch (IllegalAccessException inaccessibleField) {
                throw new IllegalStateException(inaccessibleField);
            }
        }
    }
}
