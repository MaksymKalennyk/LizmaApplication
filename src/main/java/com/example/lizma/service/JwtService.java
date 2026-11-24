package com.example.lizma.service;

import com.example.lizma.model.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration tokenTtl;

    public JwtService(@Value("${token.signing.key}") String signingKey,
                      @Value("${token.ttl:15m}") Duration tokenTtl) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(signingKey));
        this.tokenTtl = tokenTtl;
    }

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenTtl)));
        if (userDetails instanceof Users user) {
            builder.claim("id", user.getId()).claim("role", user.getRole().name());
        }
        return builder.signWith(signingKey).compact();
    }

    /** Seconds a new access token stays valid; the app refreshes it shortly before. */
    public long ttlSeconds() {
        return tokenTtl.toSeconds();
    }

    /** Who a valid token was issued to. {@code userId} is null in tokens issued before it was added. */
    public record TokenClaims(String username, Long userId) {
    }

    /**
     * Reads a valid token.
     *
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number id = claims.get("id", Number.class);
        return new TokenClaims(claims.getSubject(), id == null ? null : id.longValue());
    }
}
