package com.example.lizma.service;

import com.example.lizma.model.Users;
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
                      @Value("${token.ttl:7d}") Duration tokenTtl) {
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

    /**
     * Returns the username stored in a valid token.
     *
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public String extractUserName(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
