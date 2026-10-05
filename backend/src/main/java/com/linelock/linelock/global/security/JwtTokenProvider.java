package com.linelock.linelock.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
    
    // 토큰 서명/검증에 쓰는 비밀키(도장). 코드에서 랜덤 생성하지 않고 설정값(jwt.secret)에서 만든다.
    // 랜덤 생성하면 서버마다 키가 달라서, 한 서버가 발급한 토큰을 다른 서버가 위조로 판단해 403으로 거부하고
    // 서버를 재시작할 때마다 기존 토큰이 전부 무효화된다(서버 2대 실험에서 확인). 모든 서버가 같은 설정값을 쓰면 해결됨
    private final SecretKey key;

    // 같은 문자열이면 항상 같은 바이트 -> 같은 키가 만들어져서 서버 수/재시작과 무관하게 키가 일정함
    // 키 길이는 32바이트(256비트) 이상이어야 하며, 짧으면 라이브러리가 예외를 던짐
    // jwt.secret은 유출되면 누구나 토큰을 위조할 수 있으므로 레포에 커밋하지 않는 application.properties에만 둔다
    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

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
