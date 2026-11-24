package com.example.lizma.service;

import com.example.lizma.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing and mass sign-ups. Failed sign-ins are counted per username and per IP
 * address, sign-ups per IP address, each in a sliding window; over the limit the request gets 429 until the
 * oldest attempt leaves the window. A successful sign-in clears the username's failures.
 * <p>
 * In memory, so it is per server instance and starts over on restart: enough for one server. Behind a
 * reverse proxy, set {@code server.forward-headers-strategy=native} so the client's address is used.
 */
@Service
public class AuthRateLimiter {

    private final int failuresPerUsername;
    private final int failuresPerAddress;
    private final int signUpsPerAddress;
    private final Duration failureWindow;
    private final Duration signUpWindow;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public AuthRateLimiter(@Value("${app.auth.failures-per-username:5}") int failuresPerUsername,
                           @Value("${app.auth.failures-per-address:30}") int failuresPerAddress,
                           @Value("${app.auth.failure-window:15m}") Duration failureWindow,
                           @Value("${app.auth.sign-ups-per-address:10}") int signUpsPerAddress,
                           @Value("${app.auth.sign-up-window:1h}") Duration signUpWindow) {
        this.failuresPerUsername = failuresPerUsername;
        this.failuresPerAddress = failuresPerAddress;
        this.failureWindow = failureWindow;
        this.signUpsPerAddress = signUpsPerAddress;
        this.signUpWindow = signUpWindow;
    }

    /** Before checking a password. */
    public void checkSignIn(String username, String address) {
        check(userKey(username), failuresPerUsername, failureWindow);
        check("ip:" + address, failuresPerAddress, failureWindow);
    }

    public void signInFailed(String username, String address) {
        record(userKey(username));
        record("ip:" + address);
    }

    public void signInSucceeded(String username) {
        attempts.remove(userKey(username));
    }

    /** Counts the sign-up attempt, or refuses it when there were too many from this address. */
    public void checkSignUp(String address) {
        String key = "signup:" + address;
        check(key, signUpsPerAddress, signUpWindow);
        record(key);
    }

    private void check(String key, int limit, Duration window) {
        Deque<Instant> times = attempts.get(key);
        if (times == null) {
            return;
        }
        synchronized (times) {
            Instant cutoff = Instant.now().minus(window);
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                long minutes = Math.max(1, Duration.between(Instant.now(), times.peekFirst().plus(window)).toMinutes() + 1);
                throw new TooManyRequestsException("Too many attempts. Try again in " + minutes
                        + (minutes == 1 ? " minute." : " minutes."));
            }
        }
    }

    private void record(String key) {
        Deque<Instant> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(Instant.now());
        }
    }

    private static String userKey(String username) {
        return "user:" + (username == null ? "" : username.strip().toLowerCase(Locale.ROOT));
    }

    /** Forgets keys whose attempts are all old, so the map doesn't grow forever. */
    @Scheduled(fixedDelayString = "PT10M")
    public void forgetOldAttempts() {
        Instant cutoff = Instant.now().minus(failureWindow.compareTo(signUpWindow) > 0 ? failureWindow : signUpWindow);
        attempts.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
            }
        });
    }
}
