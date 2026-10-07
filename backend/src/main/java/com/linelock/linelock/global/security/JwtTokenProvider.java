package com.linelock.linelock.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.linelock.linelock.user.UserRole;

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
    // role도 토큰 안에 담음: 서버가 매 요청마다 DB를 조회하지 않고도 "이 사람이 ADMIN인지 USER인지" 알 수 있게 하려는 것
    // 서명으로 보호되므로 클라이언트가 role을 바꾸면 서명이 깨져서 validateToken에서 거부됨
    // 한계: 토큰이 유효한 동안(만료 전)에 DB에서 역할이 바뀌어도 이 토큰에는 반영되지 않음
    public String generateToken(String loginId, UserRole role) {
        return Jwts.builder()
                .subject(loginId)
                // 사용자 정의 클레임(이름 "role"). enum이 아니라 name() 문자열로 넣음: JWT의 값은 JSON이라 문자열이 가장 단순함
                .claim("role", role.name())
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

    // 2-1. 토큰에서 role 추출 ("ADMIN" 또는 "USER")
    // role 클레임이 없는 토큰(role을 담기 전에 발급된 옛 토큰)에서는 null을 돌려줌 -> 호출하는 쪽(필터)이 null을 처리해야 함
    // get(이름, 타입): 클레임을 이름으로 꺼내면서 원하는 타입(String)으로 받음
    public String getRole(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
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
