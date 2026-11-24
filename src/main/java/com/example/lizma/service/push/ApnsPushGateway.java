package com.example.lizma.service.push;

import com.example.lizma.model.DeviceToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Sends notifications through Apple Push Notification service: HTTP/2 to api.push.apple.com (or the sandbox
 * for development builds), signed in with a token-based key (.p8) from the Apple Developer account.
 */
@Slf4j
public class ApnsPushGateway implements PushGateway {

    /** Apple wants the sign-in token renewed after 20 and before 60 minutes. */
    private static final Duration PROVIDER_TOKEN_LIFETIME = Duration.ofMinutes(50);
    /** Answers that mean the token is no good for this app any more. */
    private static final Set<String> INVALID_TOKEN_REASONS = Set.of("BadDeviceToken", "DeviceTokenNotForTopic", "Unregistered");

    /**
     * @param teamId        Apple Developer Team ID (Membership details)
     * @param keyId         the APNs key's ID (Certificates, Identifiers & Profiles -> Keys)
     * @param topic         the app's bundle ID
     * @param productionUrl APNs for TestFlight and App Store builds
     * @param sandboxUrl    APNs for development builds
     */
    public record Settings(String teamId, String keyId, PrivateKey signingKey, String topic,
                           String productionUrl, String sandboxUrl) {
    }

    private final Settings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final Clock clock;

    private String providerToken;
    private Instant providerTokenIssuedAt;

    public ApnsPushGateway(Settings settings, ObjectMapper objectMapper, HttpClient http, Clock clock) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public Result send(DeviceToken.Environment environment, String deviceToken, PushMessage message) {
        try {
            HttpResponse<String> response = post(environment, deviceToken, message, false);
            if (response.statusCode() == 403 && "ExpiredProviderToken".equals(reason(response))) {
                response = post(environment, deviceToken, message, true);
            }
            return result(response);
        } catch (IOException e) {
            log.warn("Couldn't reach APNs: {}", e.toString());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    private HttpResponse<String> post(DeviceToken.Environment environment, String deviceToken, PushMessage message,
                                      boolean renewProviderToken) throws IOException, InterruptedException {
        String host = environment == DeviceToken.Environment.SANDBOX ? settings.sandboxUrl() : settings.productionUrl();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(host + "/3/device/" + deviceToken))
                .timeout(Duration.ofSeconds(10))
                .header("authorization", "bearer " + providerToken(renewProviderToken))
                .header("apns-topic", settings.topic())
                .header("apns-push-type", "alert")
                .header("apns-priority", "10")
                .header("apns-expiration", String.valueOf(message.expiresAt() == null ? 0 : message.expiresAt().getEpochSecond()))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(message)));
        if (message.collapseId() != null) {
            request.header("apns-collapse-id", message.collapseId());
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The JSON APNs delivers: the alert as a key of the app's strings with its arguments, plus where a tap leads. */
    String payload(PushMessage message) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("loc-key", message.type().locKey);
        alert.put("loc-args", message.args());
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");
        if (message.threadId() != null) {
            aps.put("thread-id", message.threadId());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("aps", aps);
        body.putAll(message.route());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Result result(HttpResponse<String> response) {
        if (response.statusCode() == 200) {
            return Result.SENT;
        }
        String reason = reason(response);
        if (response.statusCode() == 410 || INVALID_TOKEN_REASONS.contains(reason)) {
            return Result.INVALID_TOKEN;
        }
        log.warn("APNs didn't take a notification: {} {}", response.statusCode(), reason);
        return Result.FAILED;
    }

    private String reason(HttpResponse<String> response) {
        try {
            return objectMapper.readTree(response.body()).path("reason").asText("");
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** The JWT that signs the app's server in to APNs, reused until it's due for renewal. */
    private synchronized String providerToken(boolean renew) {
        Instant now = clock.instant();
        if (renew || providerToken == null || providerTokenIssuedAt.plus(PROVIDER_TOKEN_LIFETIME).isBefore(now)) {
            providerToken = Jwts.builder()
                    .header().keyId(settings.keyId()).and()
                    .issuer(settings.teamId())
                    .issuedAt(Date.from(now))
                    .signWith(settings.signingKey(), Jwts.SIG.ES256)
                    .compact();
            providerTokenIssuedAt = now;
        }
        return providerToken;
    }

    /** Reads an APNs key: the .p8 file's text, or just the base64 between its BEGIN and END lines. */
    public static PrivateKey readPrivateKey(String key) {
        String base64 = key.replace("\\n", "")
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("The APNs key isn't a valid .p8 key", e);
        }
    }
}
