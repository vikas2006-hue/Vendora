package com.vendora.security;

import com.vendora.web.ApiException;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 5 wrong attempts => locked for 30 seconds (per key). */
@Component
public class RateLimiter {
    private final Map<String, long[]> m = new ConcurrentHashMap<>();

    public void check(String key) {
        long[] v = m.get(key);
        long now = System.currentTimeMillis();
        if (v != null && now < v[1])
            throw new ApiException(429, "Too many attempts. Try again in " + ((v[1] - now) / 1000 + 1) + "s.");
    }
    public void fail(String key) {
        m.compute(key, (k, v) -> {
            if (v == null) v = new long[2];
            v[0]++;
            if (v[0] >= 5) { v[0] = 0; v[1] = System.currentTimeMillis() + 30_000; }
            return v;
        });
    }
    public void ok(String key) { m.remove(key); }
}
