package org.dariusturcu.backend.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

// A clock tests move forward by hand instead of sleeping.
final class MutableClock extends Clock {

    private volatile Instant current;

    MutableClock(Instant start) {
        this.current = start;
    }

    void advance(Duration duration) {
        current = current.plus(duration);
    }

    @Override
    public Instant instant() {
        return current;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
