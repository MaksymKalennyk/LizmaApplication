package com.example.lizma.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * At startup, encrypts music service tokens that were stored in plaintext before {@link TokenCipher}
 * existed. Already encrypted values are left alone, so this does nothing after the first run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptStoredTokens implements ApplicationRunner {

    private static final List<String> COLUMNS = List.of("spotify_access_token", "spotify_refresh_token", "apple_user_token");

    private final TokenCipher cipher;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int encrypted = 0;
        for (String column : COLUMNS) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("select id, " + column + " from user_music_accounts where "
                    + column + " is not null and " + column + " not like 'enc:%'").getResultList();
            for (Object[] row : rows) {
                em.createNativeQuery("update user_music_accounts set " + column + " = :value where id = :id")
                        .setParameter("value", cipher.encrypt((String) row[1]))
                        .setParameter("id", ((Number) row[0]).longValue())
                        .executeUpdate();
                encrypted++;
            }
        }
        if (encrypted > 0) {
            log.info("Encrypted {} music service tokens that were stored in plaintext", encrypted);
        }
    }
}
