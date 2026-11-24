package com.example.lizma.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encrypts secrets kept in the database (music service tokens) with AES-256-GCM, so a copy of the
 * database alone doesn't give access to anyone's Spotify account.
 * <p>
 * The key is ENCRYPTION_KEY from .env (Base64, 32 bytes: {@code openssl rand -base64 32}). Without it a key
 * is derived from the JWT signing key, which is fine for development; production should set its own.
 * Stored values look like {@code enc:v1:<key id>:<Base64 of IV and ciphertext>}; the key id lets values
 * encrypted with the derived key still be read after ENCRYPTION_KEY is introduced. Values without the
 * prefix are plaintext from before encryption and are returned as they are.
 */
@Slf4j
@Component
public class TokenCipher {

    static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Every key that can decrypt, by id; the first one encrypts. */
    private final Map<String, SecretKeySpec> keys = new LinkedHashMap<>();
    private final String currentKeyId;

    public TokenCipher(@Value("${app.encryption-key:}") String configuredKey,
                       @Value("${token.signing.key}") String jwtSigningKey) {
        SecretKeySpec derived = new SecretKeySpec(sha256("lizma:db-secrets:" + jwtSigningKey), "AES");
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(configuredKey.strip());
            if (bytes.length != 32) {
                throw new IllegalStateException("ENCRYPTION_KEY must be 32 bytes in Base64 (openssl rand -base64 32)");
            }
            add(new SecretKeySpec(bytes, "AES"));
        } else {
            log.warn("ENCRYPTION_KEY is not set: music service tokens are encrypted with a key derived from SECRET_KEY");
        }
        add(derived);
        currentKeyId = keys.keySet().iterator().next();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + currentKeyId + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't encrypt", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        String rest = stored.substring(PREFIX.length());
        int colon = rest.indexOf(':');
        SecretKeySpec key = colon > 0 ? keys.get(rest.substring(0, colon)) : null;
        if (key == null) {
            throw new IllegalStateException("A stored secret was encrypted with an unknown key; was ENCRYPTION_KEY changed?");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(rest.substring(colon + 1));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            return new String(cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("A stored secret couldn't be decrypted", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private void add(SecretKeySpec key) {
        keys.putIfAbsent(HexFormat.of().formatHex(sha256(HexFormat.of().formatHex(key.getEncoded()))).substring(0, 8), key);
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
