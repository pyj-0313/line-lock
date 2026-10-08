package com.linelock.linelock.global.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;

import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.linelock.linelock.global.exception.ErrorResponse;

import com.linelock.linelock.global.exception.ErrorCode;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

// 인증이 안 된 요청(토큰 없음, 위조, 만료)이 보호된 경로에 접근했을 때 Spring Security가 호출하는 핸들러 -> 401 JSON 응답
// 보안 필터 단계에서 나는 실패라 컨트롤러 이후의 예외만 받는 GlobalExceptionHandler가 처리하지 못해서, 여기서 직접 응답을 씀
// 컨트롤러가 아니라 ResponseEntity를 못 쓰므로 HttpServletResponse에 상태코드와 JSON 본문을 직접 기록함
// 401과 403의 차이: 401은 "누군지 모르겠다"(인증 실패), 403은 "누군지는 알지만 권한이 없다"(JwtAccessDeniedHandler)
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        // 클라이언트 쪽 문제(토큰 문제)이므로 서버 장애를 뜻하는 error가 아니라 warn. 어떤 요청이 막혔는지 운영 때 추적할 수 있게 경로를 남김
        log.warn("인증 실패: {} {}", request.getMethod(), request.getRequestURI());

        response.setStatus(errorCode.getStatus().value());
        // JSON으로 응답한다는 표시 + 한글 메시지가 깨지지 않게 UTF-8 지정
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // 다른 에러 응답과 같은 ErrorResponse 형식({"status":401,"code":"UNAUTHORIZED",...})을 JSON 문자열로 바꿔 본문에 씀
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
