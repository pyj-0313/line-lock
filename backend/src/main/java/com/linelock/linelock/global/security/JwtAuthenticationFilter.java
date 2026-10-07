package com.linelock.linelock.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

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
            String role = jwtTokenProvider.getRole(token);

            // role 클레임이 없는 토큰(role을 담기 전에 발급된 옛 토큰)은 인증 정보를 등록하지 않음 -> 인증 안 된 요청으로 취급되어 다시 로그인하게 됨
            // 정보가 부족하면 "통과"가 아니라 "거부"로 기울어야 안전함 (fail closed). 역할을 모르는 사용자를 일단 통과시키지 않으려는 것
            if (role != null) {
                // "ROLE_" 접두사는 Spring Security의 약속: 나중에 hasRole("ADMIN")이 내부적으로 "ROLE_ADMIN" 권한을 찾음
                // authorities: 이 사용자가 가진 권한 목록. 이제 토큰의 role에서 만들어서 역할 기반 접근 제어에 쓰임
                List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                // credentials 자리는 null: 이미 JWT로 검증이 끝났으니 비밀번호가 다시 필요 없음
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(loginId,
                        null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
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
