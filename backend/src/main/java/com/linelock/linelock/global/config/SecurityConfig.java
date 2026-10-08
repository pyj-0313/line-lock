package com.linelock.linelock.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.linelock.linelock.global.security.JwtAccessDeniedHandler;
import com.linelock.linelock.global.security.JwtAuthenticationEntryPoint;
import com.linelock.linelock.global.security.JwtAuthenticationFilter;
import com.linelock.linelock.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    // 401/403 응답을 만드는 핸들러. @Component 빈이라 생성자 주입으로 받음 (컨트롤러 테스트에서는 @Import로 직접 가져와야 함)
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 비밀번호를 안전하게 저장하기 위해 BCrypt 해시 함수를 사용
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF는 브라우저가 쿠키를 자동으로 보내는 세션 방식 로그인에서만 문제가 됨.
        // 우리는 매 요청마다 Authorization 헤더에 토큰을 직접 실어 보내는 JWT 방식이라 해당 공격이 성립하지 않음
        // -> 꺼도 안전함
        http.csrf(csrf -> csrf.disable());

        // JWT는 "서버가 아무것도 기억하지 않고, 매 요청의 토큰만 보고 판단"하는 stateless 방식이 핵심.
        // 기본값(세션 기반)을 끄고 STATELESS로 명시해서 서버가 세션을 만들거나 사용하지 않게 함
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // /api/auth/** (회원가입, 로그인)는 로그인하기 전에 호출하는 API라서 인증을 요구하면 앞뒤가 안 맞음 -> 예외로 전부 허용
        // /error는 컨트롤러/서비스에서 처리 안 된 예외가 터졌을 때 Spring Boot가 내부적으로 재전달(forward)하는 경로.
        // 여기를 막아두면 인증된 요청 중간에 예외가 나도 이 재전달이 다시 보안 필터를 거치며 익명 취급되어 인증 실패로 막혀버리고,
        // 클라이언트는 원래 발생한 500 대신 엉뚱한 인증 실패 응답(당시에는 403, 지금은 401)을 받게 됨
        // (비관적 락 테스트 중 실제로 겪은 문제) -> 항상 통과되도록 예외 처리
        // 규칙은 위에서부터 첫 번째로 맞는 것이 적용되므로 순서가 중요함 (더 구체적인 규칙을 먼저 둠)
        // 그 외 나머지 모든 요청은 인증(로그인 후 토큰 보유)을 요구함
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/error").permitAll()
                // 내 정보(/me)는 로그인한 누구나. /me도 아래 /api/users/** 패턴에 포함되는 경로라서 반드시 그보다 먼저 둠
                // (순서를 바꾸면 /me도 ADMIN 전용이 됨 - 규칙 순서를 뒤집어 보는 실험으로 확인함)
                .requestMatchers("/api/users/me").authenticated()
                // 그 외 /api/users/** (다른 사람의 정보)는 ADMIN만. 넓게 건 이유: 앞으로 이 아래에 경로가 추가돼도
                // 기본이 ADMIN 전용이 되어, 규칙을 빠뜨려 열려 버리는 사고를 막음 (최소 권한 기본값)
                // hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN" 권한을 확인함 (JwtAuthenticationFilter가 토큰의 role로 만든 그 권한)
                // 위반하면 컨트롤러에 닿기 전 보안 필터 단계에서 JwtAccessDeniedHandler가 403으로 응답함
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .anyRequest().authenticated());

        // 인증/인가 실패의 응답을 우리 에러 형식(ErrorResponse JSON)으로 통일함
        // 이 실패들은 컨트롤러에 닿기 전 보안 필터 단계에서 나서 @RestControllerAdvice(GlobalExceptionHandler)가 받지 못하고,
        // 설정하지 않으면 본문 없는 403이 나감 (토큰이 없는데도 401이 아니라 403이었음)
        // - authenticationEntryPoint: 인증이 안 된 요청(토큰 없음/위조/만료)이 보호된 곳에 접근했을 때 -> 401 "누군지 모르겠다"
        // - accessDeniedHandler: 인증은 됐지만 권한이 부족할 때 -> 403 "누군지는 알지만 안 된다"
        http.exceptionHandling(e -> e
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler));

        // 우리가 만든 JwtAuthenticationFilter는 아직 Spring Security 체인에 등록되지 않은 상태.
        // 기본 인증 필터(UsernamePasswordAuthenticationFilter)보다 먼저 실행되도록 등록해서,
        // 토큰 검사 및 인증 정보 등록이 먼저 끝난 뒤 이후 필터들이 그 결과를 사용하게 함
        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
