package com.example.lizma;

import com.example.lizma.config.EncryptStoredTokens;
import com.example.lizma.config.TokenCipher;
import com.example.lizma.model.UserMusicAccount;
import com.example.lizma.model.enums.MusicProvider;
import com.example.lizma.repo.UserMusicAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Music service tokens never sit in the database in plaintext. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenEncryptionTest extends ApiTestSupport {

    private static final String JWT_KEY = "bGl6bWEtdGVzdC1zaWduaW5nLWtleS0zMi1ieXRlcyE=";

    @Autowired
    private UserMusicAccountRepository accounts;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EncryptStoredTokens encryptStoredTokens;

    @Test
    void tokensAreEncryptedInTheDatabaseAndPlainInTheApp() throws Exception {
        User user = newUser();
        UserMusicAccount saved = accounts.save(UserMusicAccount.builder()
                .user(userRepository.findById(user.id()).orElseThrow())
                .provider(MusicProvider.SPOTIFY)
                .spotifyAccessToken("BQD-access-token-from-spotify")
                .spotifyRefreshToken("AQC-refresh-token-from-spotify")
                .spotifyAccessTokenExpiresAt(Instant.now().plusSeconds(3600))
                .build());

        String storedAccess = column("spotify_access_token", saved.getId());
        String storedRefresh = column("spotify_refresh_token", saved.getId());
        assertThat(storedAccess).startsWith("enc:v1:").doesNotContain("BQD-access-token");
        assertThat(storedRefresh).startsWith("enc:v1:").doesNotContain("AQC-refresh");

        UserMusicAccount loaded = accounts.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getSpotifyAccessToken()).isEqualTo("BQD-access-token-from-spotify");
        assertThat(loaded.getSpotifyRefreshToken()).isEqualTo("AQC-refresh-token-from-spotify");
    }

    @Test
    void plaintextTokensFromBeforeAreReadAndEncryptedAtStartup() throws Exception {
        User user = newUser();
        jdbc.update("insert into user_music_accounts (provider, spotify_access_token, spotify_refresh_token, user_id) "
                + "values ('SPOTIFY', 'old-plain-access', 'old-plain-refresh', ?)", user.id());
        Long id = jdbc.queryForObject("select id from user_music_accounts where user_id = ?", Long.class, user.id());

        assertThat(accounts.findById(id).orElseThrow().getSpotifyAccessToken()).isEqualTo("old-plain-access");

        encryptStoredTokens.run(null);
        assertThat(column("spotify_access_token", id)).startsWith("enc:v1:");
        assertThat(column("spotify_refresh_token", id)).startsWith("enc:v1:");
        assertThat(accounts.findById(id).orElseThrow().getSpotifyRefreshToken()).isEqualTo("old-plain-refresh");
    }

    @Test
    void valuesFromTheDerivedKeyStayReadableAfterAnEncryptionKeyIsSet() {
        TokenCipher withoutKey = new TokenCipher("", JWT_KEY);
        String stored = withoutKey.encrypt("secret");

        String newKey = Base64.getEncoder().encodeToString(new byte[32]);
        TokenCipher withKey = new TokenCipher(newKey, JWT_KEY);
        assertThat(withKey.decrypt(stored)).isEqualTo("secret");
        String restored = withKey.encrypt("secret");
        assertThat(restored).isNotEqualTo(stored);
        assertThat(withKey.decrypt(restored)).isEqualTo("secret");
        // The derived key alone can't read what the new key wrote.
        assertThatThrownBy(() -> withoutKey.decrypt(restored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTamperedValueIsRejected() {
        TokenCipher cipher = new TokenCipher("", JWT_KEY);
        String stored = cipher.encrypt("secret");
        String tampered = stored.substring(0, stored.length() - 4) + (stored.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
        assertThatThrownBy(() -> new TokenCipher("c2hvcnQ=", JWT_KEY)).isInstanceOf(IllegalStateException.class);
    }

    private String column(String name, Long id) {
        return jdbc.queryForObject("select " + name + " from user_music_accounts where id = ?", String.class, id);
    }
}
