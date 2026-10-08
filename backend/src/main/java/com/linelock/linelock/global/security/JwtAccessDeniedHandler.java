package com.linelock.linelock.global.security;

import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.http.MediaType;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import com.linelock.linelock.global.exception.ErrorResponse;

import com.linelock.linelock.global.exception.ErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// 인증은 됐지만(누군지는 아는데) 권한이 부족한 요청에 Spring Security가 호출하는 핸들러 -> 403 JSON 응답
// 예: 일반 사용자(USER)가 ADMIN 전용 경로에 접근. JwtAuthenticationEntryPoint(401)와 짝을 이루며,
// 같은 이유(보안 필터 단계라 GlobalExceptionHandler가 못 받음)로 HttpServletResponse에 직접 응답을 씀
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        // 권한이 없는 접근 시도는 보안상 기록해둘 가치가 있어서 경로를 남김 (클라이언트 문제라 warn)
        log.warn("권한 없음: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
