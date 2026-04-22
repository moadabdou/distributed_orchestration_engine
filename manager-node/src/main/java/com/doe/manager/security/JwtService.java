package com.doe.manager.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey jwtSecretKey;

    public JwtService(@Value("${manager.security.jwt.secret:default-secret}") String jwtSecret) {
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateJobToken(UUID workflowId, UUID jobId) {
        String sub = (workflowId != null ? workflowId.toString() : "none") + ":" + jobId.toString();
        return Jwts.builder()
                .subject(sub)
                .signWith(jwtSecretKey)
                .compact();
    }

    public String validateAndExtractSubject(String token) {
        return Jwts.parser()
                .verifyWith(jwtSecretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
