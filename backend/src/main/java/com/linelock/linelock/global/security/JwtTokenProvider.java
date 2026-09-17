package com.linelock.linelock.global.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Component
public class JwtTokenProvider {
    
    private final SecretKey key = Jwts.SIG.HS256.key().build();

    // 1. 토큰 발급
    public String generateToken(String loginId) {
        return Jwts.builder()
                .subject(loginId)
                .issuedAt(new Date())
                .expiration(new
            Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    // 2. 토큰에서 loginId 추출
    public String getLoginId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String loginId = claims.getSubject();
        return loginId;

    }

    // 3. 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
        Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return true;
        } catch (JwtException e) {
            return false;
        }
    }
    
}
