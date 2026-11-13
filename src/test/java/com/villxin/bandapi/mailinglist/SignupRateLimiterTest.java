package com.villxin.bandapi.mailinglist;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupRateLimiterTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-10T12:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();

    @Test
    void perClientLimitResetsAfterItsWindow() {
        SignupRateLimiter limiter = new SignupRateLimiter(2, Duration.ofMinutes(10), 100, Duration.ofHours(1), clock);

        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
        assertFalse(limiter.tryAcquire("1.2.3.4"));
        assertTrue(limiter.tryAcquire("5.6.7.8"), "other clients are unaffected");

        clock.advance(Duration.ofMinutes(10));
        assertTrue(limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    void globalCapHoldsEvenWhenEveryRequestClaimsANewIp() {
        SignupRateLimiter limiter = new SignupRateLimiter(5, Duration.ofMinutes(10), 3, Duration.ofHours(1), clock);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"));
        assertTrue(limiter.tryAcquire("10.0.0.3"));
        assertFalse(limiter.tryAcquire("10.0.0.4"));

        clock.advance(Duration.ofHours(1));
        assertTrue(limiter.tryAcquire("10.0.0.5"));
    }

    @Test
    void refusedAttemptsDontUseUpSiteWideSlots() {
        SignupRateLimiter limiter = new SignupRateLimiter(1, Duration.ofMinutes(10), 2, Duration.ofHours(1), clock);

        assertTrue(limiter.tryAcquire("bot"));
        for (int i = 0; i < 50; i++) {
            assertFalse(limiter.tryAcquire("bot"));
        }
        assertTrue(limiter.tryAcquire("fan"), "the bot's blocked retries must not lock out a real fan");
    }
}
