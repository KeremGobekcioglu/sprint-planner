package com.sprintplanner.infrastructure.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Protects the (free-tier) Gemini key from being drained by one user or by the public.
 *
 * Every AI-triggering endpoint calls {@link #consume} with a "weight" roughly proportional
 * to the number of Gemini requests it fires (a full sprint plan is ~4 requests).
 * Limits are rolling windows, kept in memory (reset on restart) - fine for a single instance.
 */
@Component
public class AiUsageLimiter {

    public static final int WEIGHT_PLAN = 4;
    public static final int WEIGHT_SINGLE = 1;

    private static final long HOUR_MS = 60L * 60 * 1000;
    private static final long DAY_MS = 24 * HOUR_MS;

    private final int perUserHourly;
    private final int perUserDaily;
    private final int globalDaily;

    private final ConcurrentHashMap<UUID, Deque<long[]>> perUser = new ConcurrentHashMap<>();
    private final Deque<long[]> global = new ArrayDeque<>();

    public AiUsageLimiter(
        @Value("${app.ai.per-user-hourly:16}") int perUserHourly,
        @Value("${app.ai.per-user-daily:40}") int perUserDaily,
        @Value("${app.ai.global-daily:200}") int globalDaily
    ) {
        this.perUserHourly = perUserHourly;
        this.perUserDaily = perUserDaily;
        this.globalDaily = globalDaily;
    }

    /** Throws 429 if the request would exceed a limit; otherwise records the usage. */
    public void consume(UUID userId, boolean admin, int weight) {
        long now = System.currentTimeMillis();

        synchronized (global) {
            prune(global, now - DAY_MS);
            if (sum(global) + weight > globalDaily) {
                throw tooMany("The shared AI quota for today is used up. Please try again tomorrow.");
            }

            if (!admin) {
                Deque<long[]> mine = perUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
                synchronized (mine) {
                    prune(mine, now - DAY_MS);
                    if (sum(mine) + weight > perUserDaily) {
                        throw tooMany("Daily AI limit reached for your account. Please try again tomorrow.");
                    }
                    if (sumSince(mine, now - HOUR_MS) + weight > perUserHourly) {
                        throw tooMany("Hourly AI limit reached for your account. Please wait a while.");
                    }
                    mine.addLast(new long[] {now, weight});
                }
            }
            global.addLast(new long[] {now, weight});
        }

        if (perUser.size() > 5_000) {
            perUser.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue(), now - DAY_MS);
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    private static ResponseStatusException tooMany(String message) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    private static void prune(Deque<long[]> d, long cutoff) {
        while (!d.isEmpty() && d.peekFirst()[0] < cutoff) {
            d.pollFirst();
        }
    }

    private static int sum(Deque<long[]> d) {
        return sumSince(d, Long.MIN_VALUE);
    }

    private static int sumSince(Deque<long[]> d, long since) {
        int total = 0;
        for (long[] e : d) {
            if (e[0] >= since) total += (int) e[1];
        }
        return total;
    }
}
