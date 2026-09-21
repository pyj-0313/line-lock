package com.linelock.linelock.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.linelock.linelock.global.security.JwtAuthenticationFilter;
import com.linelock.linelock.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor 
public class SecurityConfig {
    
    private final JwtTokenProvider jwtTokenProvider;
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 비밀번호를 안전하게 저장하기 위해 BCrypt 해시 함수를 사용
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF는 브라우저가 쿠키를 자동으로 보내는 세션 방식 로그인에서만 문제가 됨.
        // 우리는 매 요청마다 개발자가 직접 Authorization 헤더에 토큰을 실어 보내는 JWT 방식이라 해당 공격이 성립하지 않음 -> 꺼도 안전함
        http.csrf(csrf -> csrf.disable());

        // JWT는 "서버가 아무것도 기억하지 않고, 매 요청의 토큰만 보고 판단"하는 stateless 방식이 핵심.
        // 기본값(세션 기반)을 끄고 STATELESS로 명시해서 서버가 세션을 만들거나 사용하지 않게 함
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // /api/auth/** (회원가입, 로그인)는 로그인하기 전에 호출하는 API라서 인증을 요구하면 앞뒤가 안 맞음 -> 예외로 전부 허용
        // 그 외 나머지 모든 요청은 인증(로그인 후 토큰 보유)을 요구함
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated());

        // 우리가 만든 JwtAuthenticationFilter는 아직 Spring Security 체인에 등록되지 않은 상태.
        // 기본 인증 필터(UsernamePasswordAuthenticationFilter)보다 먼저 실행되도록 등록해서,
        // 토큰 검사 및 인증 정보 등록이 먼저 끝난 뒤 이후 필터들이 그 결과를 사용하게 함
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
