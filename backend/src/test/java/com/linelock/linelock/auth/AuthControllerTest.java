package com.linelock.linelock.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.linelock.linelock.auth.dto.LoginRequest;
import com.linelock.linelock.auth.dto.LoginResponse;
import com.linelock.linelock.auth.dto.SignupRequest;
import com.linelock.linelock.global.config.SecurityConfig;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.global.security.JwtTokenProvider;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class) // Spring Boot 전체가 아니라 웹 계층(AuthController + MVC 관련)만 가볍게 띄움
@Import(SecurityConfig.class) // 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
                              // (안 그러면 Spring Boot의 기본 보안이 대신 적용되어 실제 앱과 다르게 동작함)
public class AuthControllerTest {

    @Autowired // 실제 HTTP 요청을 흉내 내는 도구를 Spring 컨테이너에서 주입받음
    private MockMvc mockMvc;

    @MockitoBean // Spring 컨테이너 안에 등록되는 가짜 AuthService (진짜 DB/암호화 없이 Controller만 검증하려는 목적)
    private AuthService authService;

    @MockitoBean // SecurityConfig(JwtAuthenticationFilter)가 필요로 하는 의존성이라 같이 가짜로 준비해야 함
    private JwtTokenProvider jwtTokenProvider;

    // signup/login은 SecurityConfig에서 permitAll()로 열려 있는 엔드포인트라 @WithMockUser 없이 테스트함
    // (인증 없이 호출해도 성공해야 하는 게 진짜 동작이므로, 로그인한 척을 하면 오히려 검증의 의미가 사라짐)

    @Test
    void signup_성공() throws Exception {
        // given: 회원가입 요청 데이터 준비. signup()은 void라서 when(...).thenReturn(...)으로 약속할 값이 없고,
        // 가짜 객체는 void 메서드를 호출하면 기본적으로 아무것도 안 하므로 따로 설정하지 않아도 됨
        SignupRequest signupRequest = new SignupRequest("test", "1234", "테스트");

        // when & then: POST 요청 본문에 JSON으로 담아 보내고 200 응답인지 검증
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(signupRequest)))
                .andExpect(status().isOk());

        // then: 리턴값이 없으니 "서비스가 실제로 호출됐는지"로 검증
        verify(authService).signup(any(SignupRequest.class));
    }

    @Test
    void login_성공() throws Exception {
        // given: login()이 호출되면 토큰이 담긴 LoginResponse를 리턴하도록 약속(stubbing)
        LoginRequest loginRequest = new LoginRequest("test", "1234");

        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse("token123"));

        // when & then: 200 응답이고, 응답 JSON의 token 필드가 약속한 값과 같은지 검증
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token123"));
    }

    @Test
    void signup_중복아이디면_409() throws Exception {
        // given: signup()은 void라서 when(...).thenThrow(...)를 못 씀 -> doThrow(예외).when(가짜).메서드() 순서로 약속
        // (when(...) 안에 넣을 "리턴값"이 없어서 순서가 거꾸로인 문법을 쓰는 것)
        SignupRequest signupRequest = new SignupRequest("test", "1234", "테스트");
        doThrow(new CustomException(ErrorCode.DUPLICATE_LOGIN_ID))
                .when(authService).signup(any(SignupRequest.class));

        // when & then: 이미 있는 값과 충돌하는 요청이라 409로 응답되고 code가 DUPLICATE_LOGIN_ID인지 검증
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(signupRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
    }

    @Test
    void login_실패하면_401() throws Exception {
        // given: login()은 값을 리턴하는 메서드라 when(...).thenThrow(...)를 사용. 아이디가 없든 비밀번호가 틀리든
        // 서비스는 똑같이 LOGIN_FAILED를 던지므로(계정 열거 공격 방지), 여기서도 하나의 경우로만 검증하면 충분함
        LoginRequest loginRequest = new LoginRequest("test", "wrong");
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException(ErrorCode.LOGIN_FAILED));

        // when & then: 인증 실패라 401로 응답되고 code가 LOGIN_FAILED인지 검증
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }
}