package com.linelock.linelock.global.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.Collections;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        // token이 null이면 validateToken 자체를 호출하지 않도록 null 체크를 먼저 함
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String loginId = jwtTokenProvider.getLoginId(token);
            // credentials 자리는 null: 이미 JWT로 검증이 끝났으니 비밀번호가 다시 필요 없음
            // authorities 자리는 빈 리스트: 아직 역할(Role) 기반 인가 로직을 구현하지 않음
            UsernamePasswordAuthenticationToken authentication = new
            UsernamePasswordAuthenticationToken(loginId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // 인증 성공 여부와 무관하게 항상 다음 필터로 요청을 넘겨야 함 (안 그러면 요청이 여기서 멈춤)
        filterChain.doFilter(request, response);

    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            // "Bearer "는 공백 포함 7글자라서, 그 뒤(인덱스 7)부터가 순수 토큰 값
            return bearerToken.substring(7);
        }
        return null;
    }
}
