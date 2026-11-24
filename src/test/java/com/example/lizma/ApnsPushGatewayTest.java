package com.example.lizma;

import com.example.lizma.model.DeviceToken;
import com.example.lizma.service.push.ApnsPushGateway;
import com.example.lizma.service.push.PushGateway;
import com.example.lizma.service.push.PushMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The APNs client against a stand-in for Apple's server: the request it makes and how it reads the answers. */
class ApnsPushGatewayTest {

    private static final String DEVICE = "0f".repeat(32);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-24T10:00:00Z"));
    private KeyPair keys;
    private HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    /** Answers to give, in order; 200 with an empty body once they run out. */
    private final Deque<int[]> statuses = new ArrayDeque<>();
    private final Deque<String> bodies = new ArrayDeque<>();
    private ApnsPushGateway gateway;

    record Request(String path, Map<String, String> headers, String body) {
    }

    @BeforeEach
    void start() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keys = generator.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        gateway = new ApnsPushGateway(
                new ApnsPushGateway.Settings("TEAM123456", "KEY1234567", keys.getPrivate(), "maxos-industry.lizma",
                        base + "/production", base + "/sandbox"),
                objectMapper, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheAlertAsTheAppsOwnStringSignedWithTheTeamsKey() throws Exception {
        PushMessage message = new PushMessage(PushMessage.Type.COMMENT, List.of("Liza", "nice one"), "post-68", null,
                Instant.parse("2026-11-25T10:00:00Z"), Map.of("type", "comment", "postId", 68L, "commentId", 7L));

        assertThat(gateway.send(DeviceToken.Environment.PRODUCTION, DEVICE, message)).isEqualTo(PushGateway.Result.SENT);

        Request request = requests.get(0);
        assertThat(request.path()).isEqualTo("/production/3/device/" + DEVICE);
        assertThat(request.headers()).containsEntry("apns-topic", "maxos-industry.lizma")
                .containsEntry("apns-push-type", "alert")
                .containsEntry("apns-priority", "10")
                .containsEntry("apns-expiration", String.valueOf(Instant.parse("2026-11-25T10:00:00Z").getEpochSecond()))
                .doesNotContainKey("apns-collapse-id");

        Jws<Claims> signIn = Jwts.parser().verifyWith((ECPublicKey) keys.getPublic()).build()
                .parseSignedClaims(request.headers().get("authorization").substring("bearer ".length()));
        assertThat(signIn.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(signIn.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(signIn.getPayload().getIssuer()).isEqualTo("TEAM123456");

        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.at("/aps/alert/loc-key").asText()).isEqualTo("PUSH_COMMENT");
        assertThat(body.at("/aps/alert/loc-args/0").asText()).isEqualTo("Liza");
        assertThat(body.at("/aps/alert/loc-args/1").asText()).isEqualTo("nice one");
        assertThat(body.at("/aps/thread-id").asText()).isEqualTo("post-68");
        assertThat(body.at("/aps/sound").asText()).isEqualTo("default");
        assertThat(body.get("type").asText()).isEqualTo("comment");
        assertThat(body.get("postId").asLong()).isEqualTo(68);
        assertThat(body.get("commentId").asLong()).isEqualTo(7);
    }

    @Test
    void developmentPhonesGoToTheSandboxAndRemindersReplaceEachOther() {
        PushMessage reminder = new PushMessage(PushMessage.Type.DAILY_REMINDER, List.of(), "daily", "daily-reminder",
                null, Map.of("type", "daily_reminder"));

        gateway.send(DeviceToken.Environment.SANDBOX, DEVICE, reminder);

        assertThat(requests.get(0).path()).startsWith("/sandbox/3/device/");
        assertThat(requests.get(0).headers()).containsEntry("apns-collapse-id", "daily-reminder")
                .containsEntry("apns-expiration", "0");
    }

    @Test
    void aWidgetRefreshIsASilentBackgroundPush() throws Exception {
        PushMessage refresh = new PushMessage(PushMessage.Type.WIDGET_REFRESH, List.of(), null, null, null,
                Map.of("type", "widget_refresh"));

        gateway.send(DeviceToken.Environment.PRODUCTION, DEVICE, refresh);

        Request request = requests.get(0);
        assertThat(request.headers()).containsEntry("apns-push-type", "background").containsEntry("apns-priority", "5");
        JsonNode body = objectMapper.readTree(request.body());
        assertThat(body.at("/aps/content-available").asInt()).isEqualTo(1);
        assertThat(body.at("/aps/alert").isMissingNode()).isTrue();
        assertThat(body.get("type").asText()).isEqualTo("widget_refresh");
    }

    @Test
    void aTokenAppleNoLongerKnowsIsReportedSoItCanBeForgotten() {
        answer(410, "{\"reason\":\"Unregistered\",\"timestamp\":1700000000000}");
        answer(400, "{\"reason\":\"BadDeviceToken\"}");
        answer(400, "{\"reason\":\"BadTopic\"}");
        answer(429, "{\"reason\":\"TooManyRequests\"}");
        answer(503, "");

        assertThat(List.of(send(), send(), send(), send(), send())).containsExactly(
                PushGateway.Result.INVALID_TOKEN, PushGateway.Result.INVALID_TOKEN,
                PushGateway.Result.FAILED, PushGateway.Result.FAILED, PushGateway.Result.FAILED);
    }

    @Test
    void theSignInTokenIsReusedForAWhileAndRenewedWhenAppleSaysItExpired() {
        send();
        clock.move(Duration.ofMinutes(10));
        send();
        assertThat(requests.get(1).headers().get("authorization")).isEqualTo(requests.get(0).headers().get("authorization"));

        clock.move(Duration.ofMinutes(45)); // 55 minutes old: due
        send();
        assertThat(requests.get(2).headers().get("authorization")).isNotEqualTo(requests.get(1).headers().get("authorization"));

        clock.move(Duration.ofMinutes(1));
        answer(403, "{\"reason\":\"ExpiredProviderToken\"}");
        assertThat(send()).isEqualTo(PushGateway.Result.SENT);
        assertThat(requests).hasSize(5);
        assertThat(requests.get(4).headers().get("authorization")).isNotEqualTo(requests.get(3).headers().get("authorization"));
    }

    @Test
    void theKeyCanBeTheP8FileOrJustItsBase64() {
        String base64 = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        assertThat(ApnsPushGateway.readPrivateKey(pem).getEncoded()).isEqualTo(keys.getPrivate().getEncoded());
        assertThat(ApnsPushGateway.readPrivateKey(base64).getEncoded()).isEqualTo(keys.getPrivate().getEncoded());
        assertThat(ApnsPushGateway.readPrivateKey(pem.replace("\n", "\\n")).getEncoded()).isEqualTo(keys.getPrivate().getEncoded());
        assertThatThrownBy(() -> ApnsPushGateway.readPrivateKey("not a key")).isInstanceOf(IllegalStateException.class);
    }

    private PushGateway.Result send() {
        return gateway.send(DeviceToken.Environment.PRODUCTION, DEVICE, new PushMessage(PushMessage.Type.NEW_FRIEND,
                List.of("Liza"), "friends", null, null, Map.of("type", "new_friend", "username", "lizka")));
    }

    private void answer(int status, String body) {
        statuses.add(new int[]{status});
        bodies.add(body);
    }

    private void answer(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new java.util.HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
        requests.add(new Request(exchange.getRequestURI().getPath(), headers,
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        int[] status = statuses.poll();
        String body = bodies.poll();
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status == null ? 200 : status[0], bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    /** A clock the test moves forward by hand. */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void move(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
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
}
