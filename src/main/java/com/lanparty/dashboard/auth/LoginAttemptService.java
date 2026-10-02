package com.lanparty.dashboard.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/** Locks the admin login for a client after too many wrong codes (5 attempts → 5 minutes). */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Remaining lock time, or {@link Duration#ZERO} when the client may try again. */
    public Duration lockRemaining(String client) {
        Attempts a = attempts.get(client);
        if (a == null || a.lockedUntil == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), a.lockedUntil);
        if (remaining.isNegative() || remaining.isZero()) {
            attempts.remove(client);
            return Duration.ZERO;
        }
        return remaining;
    }

    public void failed(String client) {
        attempts.compute(client, (k, a) -> {
            Attempts next = a == null ? new Attempts() : a;
            next.failures++;
            if (next.failures >= MAX_ATTEMPTS) {
                next.lockedUntil = clock.instant().plus(LOCK_DURATION);
            }
            return next;
        });
    }

    public void succeeded(String client) {
        attempts.remove(client);
    }

    private static final class Attempts {
        int failures;
        Instant lockedUntil;
    }
}
