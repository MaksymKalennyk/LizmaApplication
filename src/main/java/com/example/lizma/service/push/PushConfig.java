package com.example.lizma.service.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/** APNs when its key is configured (APNS_* in .env), otherwise notifications only go to the log. */
@Slf4j
@Configuration
public class PushConfig {

    @Bean
    PushGateway pushGateway(@Value("${app.push.apns.team-id:}") String teamId,
                            @Value("${app.push.apns.key-id:}") String keyId,
                            @Value("${app.push.apns.key-path:}") String keyPath,
                            @Value("${app.push.apns.key:}") String key,
                            @Value("${app.push.apns.topic:maxos-industry.lizma}") String topic,
                            @Value("${app.push.apns.production-url:https://api.push.apple.com}") String productionUrl,
                            @Value("${app.push.apns.sandbox-url:https://api.sandbox.push.apple.com}") String sandboxUrl,
                            ObjectMapper objectMapper) {
        if (teamId.isBlank() || keyId.isBlank() || (keyPath.isBlank() && key.isBlank())) {
            log.info("Push notifications are only written to the log: APNs isn't set up (APNS_TEAM_ID, APNS_KEY_ID, APNS_KEY_PATH)");
            return new LogOnlyPushGateway();
        }
        String keyText = key.isBlank() ? read(keyPath) : key;
        ApnsPushGateway.Settings settings = new ApnsPushGateway.Settings(teamId.trim(), keyId.trim(),
                ApnsPushGateway.readPrivateKey(keyText), topic.trim(), productionUrl, sandboxUrl);
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("Push notifications go through APNs (key {}, app {})", settings.keyId(), settings.topic());
        return new ApnsPushGateway(settings, objectMapper, http, Clock.systemUTC());
    }

    private static String read(String keyPath) {
        try {
            return Files.readString(Path.of(keyPath.trim()));
        } catch (IOException e) {
            throw new IllegalStateException("Can't read the APNs key file " + keyPath + " (APNS_KEY_PATH)", e);
        }
    }
}
