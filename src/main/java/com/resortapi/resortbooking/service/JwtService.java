package com.resortapi.resortbooking.service;

import com.resortapi.resortbooking.entity.AppUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-seconds}") long expirySeconds) {
        // HS256 needs at least 256 bits of key material.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = expirySeconds;
    }

    /**
     * No role claim: JwtAuthenticationFilter re-derives the caller's current role
     * from the database on every request (via the subject only), so a claim here
     * would be dead weight at best - and at worst an invitation to someday read it
     * back for an authorization decision a tampered token could then forge.
     */
    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                // Pinned: signWith(key) alone picks the algorithm from key length.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Throws JwtException if the signature, format or expiry is wrong. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}
