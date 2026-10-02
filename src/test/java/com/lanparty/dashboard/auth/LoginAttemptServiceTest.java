package com.lanparty.dashboard.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    @Test
    void locksAfterFiveFailuresForFiveMinutes() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-17T12:00:00Z"));
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        LoginAttemptService service = new LoginAttemptService(clock);
        for (int i = 0; i < 4; i++) {
            service.failed("1.2.3.4");
        }
        assertThat(service.lockRemaining("1.2.3.4")).isZero();
        service.failed("1.2.3.4");
        assertThat(service.lockRemaining("1.2.3.4")).isEqualTo(Duration.ofMinutes(5));
        assertThat(service.lockRemaining("5.6.7.8")).isZero();

        now.set(now.get().plus(Duration.ofMinutes(5)));
        assertThat(service.lockRemaining("1.2.3.4")).isZero();
    }
}
