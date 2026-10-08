package com.linelock.linelock.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;

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
import com.linelock.linelock.global.security.JwtAccessDeniedHandler;
import com.linelock.linelock.global.security.JwtAuthenticationEntryPoint;
import com.linelock.linelock.global.security.JwtTokenProvider;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class) // Spring Boot 전체가 아니라 웹 계층(AuthController + MVC 관련)만 가볍게 띄움
// 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
// (안 그러면 Spring Boot의 기본 보안이 대신 적용되어 실제 앱과 다르게 동작함)
// SecurityConfig가 요구하는 401/403 핸들러(@Component)도 웹 계층 테스트에는 자동으로 안 올라와서 함께 가져옴 (빠지면 NoSuchBeanDefinitionException)
@Import({ SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class })
public class AuthControllerTest {

        @Autowired // 실제 HTTP 요청을 흉내 내는 도구를 Spring 컨테이너에서 주입받음
        private MockMvc mockMvc;

        @MockitoBean // Spring 컨테이너 안에 등록되는 가짜 AuthService (진짜 DB/암호화 없이 Controller만 검증하려는 목적)
        private AuthService authService;

        @MockitoBean // SecurityConfig(JwtAuthenticationFilter)가 필요로 하는 의존성이라 같이 가짜로 준비해야 함
        private JwtTokenProvider jwtTokenProvider;

        // signup/login은 SecurityConfig에서 permitAll()로 열려 있는 엔드포인트라 @WithMockUser 없이
        // 테스트함
        // (인증 없이 호출해도 성공해야 하는 게 진짜 동작이므로, 로그인한 척을 하면 오히려 검증의 의미가 사라짐)

        @Test
        void signup_성공() throws Exception {
                // given: 회원가입 요청 데이터 준비. signup()은 void라서 when(...).thenReturn(...)으로 약속할 값이
                // 없고,
                // 가짜 객체는 void 메서드를 호출하면 기본적으로 아무것도 안 하므로 따로 설정하지 않아도 됨
                SignupRequest signupRequest = new SignupRequest("test", "12345678", "테스트");

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
                // 비밀번호가 4자("1234")여도 통과하는 게 정상: 로그인에는 비어 있지 않은지(@NotBlank)만 검사하고 길이 규칙은 없음
                // (가입 테스트는 8자 규칙 때문에 "12345678"을 쓰는 것과 대비됨)
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
                // given: signup()은 void라서 when(...).thenThrow(...)를 못 씀 ->
                // doThrow(예외).when(가짜).메서드() 순서로 약속
                // (when(...) 안에 넣을 "리턴값"이 없어서 순서가 거꾸로인 문법을 쓰는 것)
                SignupRequest signupRequest = new SignupRequest("test", "12345678", "테스트");
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

        @Test
        void 깨진_JSON을_보내면_400() throws Exception {
                // when & then: 본문이 JSON 형식이 아니면(여기선 일부러 닫는 중괄호가 없는 깨진 JSON) Spring이 읽지 못해
                // HttpMessageNotReadableException이 터짐. 요청 쪽 문제이므로 400 + INVALID_REQUEST로 응답되는지
                // 검증
                // (서비스까지 가지 못하는 단계의 실패라 stubbing 없이 검증 가능)
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{깨진 JSON"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        void login_빈_값이면_400과_필드별_사유() throws Exception {
                // 이 테스트의 목적: @NotBlank 규칙이 실제로 검사되고(@Valid), 어느 필드가 왜 틀렸는지 응답에 담기는지 확인
                // (규칙만 선언하고 @Valid를 빼먹거나 핸들러가 없으면 각각 200, 500이 나옴 - 직접 겪음)

                // given: 아이디와 비밀번호를 모두 빈 문자열로 보내는 요청. 서비스를 stubbing하지 않는 이유는
                // 컨트롤러에 들어오기 전 검증 단계에서 이미 걸러져 서비스까지 가지 않기 때문
                LoginRequest loginRequest = new LoginRequest("", "");

                // when & then: 검증에서 걸려 400 + INVALID_REQUEST이고, 틀린 두 필드와 한국어 사유가 fieldErrors에
                // 담기는지 검증
                // containsInAnyOrder: 검증 오류 목록의 순서는 보장되지 않아서 [0], [1]로 위치를 고정하지 않고
                // "순서와 상관없이 이 값들이 들어 있는지"로 확인함. $.fieldErrors[*]의 [*]는 배열의 모든 항목이라는 뜻
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(loginRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                                .andExpect(jsonPath("$.fieldErrors[*].field",
                                                containsInAnyOrder("loginId", "password")))
                                .andExpect(jsonPath("$.fieldErrors[*].message",
                                                containsInAnyOrder("아이디를 입력해주세요.", "비밀번호를 입력해주세요.")));

        }

        @Test
        void signup_비밀번호가_8자_미만이면_400() throws Exception {
                // given: 아이디와 이름은 정상이고 비밀번호(4자)만 @Size(min = 8) 규칙을 어기는 가입 요청
                // 가입에는 길이 규칙을 걸고 로그인에는 안 거는 이유: 가입은 비밀번호를 "새로 정하는" 시점이라 지금 규칙을 요구할 수 있지만,
                // 로그인은 이미 정해진 값을 해시와 비교하는 시점이라 규칙이 나중에 바뀌어도 옛 사용자가 로그인할 수 있어야 함
                SignupRequest signupRequest = new SignupRequest("test", "1234", "테스트");

                // when & then: 규칙을 하나만 어겼으므로 fieldErrors에 password 하나만 담기는지 검증
                // (오류가 하나뿐이라 [0]으로 확인해도 순서 문제가 없음. 오류가 여러 개일 땐 위 테스트처럼 순서 무관 방식을 씀)
                // 메시지 문자열은 SignupRequest의 @Size(message = ...)와 한 글자도 다르면 안 됨
                mockMvc.perform(post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(signupRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                                .andExpect(jsonPath("$.fieldErrors[0].message").value("비밀번호는 8자 이상이어야 합니다."));
        }
}