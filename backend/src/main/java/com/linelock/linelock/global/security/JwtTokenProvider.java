package com.linelock.linelock.global.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Component
public class JwtTokenProvider {
    
    // 서버가 재시작될 때마다 새로 생성됨 -> 재시작 이전에 발급된 토큰은 전부 서명 불일치로 무효화됨
    // (운영에서는 이 키를 application.properties 등에 고정값으로 저장해두는 방식으로 개선 필요)
    private final SecretKey key = Jwts.SIG.HS256.key().build();

    // 1. 토큰 발급
    public String generateToken(String loginId) {
        return Jwts.builder()
                .subject(loginId)
                .issuedAt(new Date())
                // 3600000ms(1시간) 후 만료 - 만료 시점을 넘긴 토큰은 validateToken에서 자동으로 거부됨
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
        // 파싱 결과 자체는 필요 없고, 예외 없이 끝까지 실행되는지(=서명/형식/만료 모두 정상인지)만 확인
        Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return true;
        } catch (JwtException e) {
            // 서명 위조, 형식 오류, 만료 등 어떤 사유든 JwtException 계열로 통일되어 던져짐
            return false;
        }
    }
    
}
