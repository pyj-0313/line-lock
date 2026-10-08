package com.linelock.linelock.user;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.linelock.linelock.global.config.SecurityConfig;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.global.security.JwtAccessDeniedHandler;
import com.linelock.linelock.global.security.JwtAuthenticationEntryPoint;
import com.linelock.linelock.global.security.JwtTokenProvider;

@WebMvcTest(UserController.class) // Spring Boot 전체가 아니라 웹 계층(UserController + MVC 관련)만 가볍게 띄움
// 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
// SecurityConfig가 요구하는 401/403 핸들러(@Component)도 웹 계층 테스트에는 자동으로 안 올라와서 함께 가져옴
// (빠지면 NoSuchBeanDefinitionException)
@Import({ SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class })
public class UserControllerTest {

    @Autowired // 실제 HTTP 요청을 흉내 내는 도구를 Spring 컨테이너에서 주입받음
    private MockMvc mockMvc;

    @MockitoBean // Spring 컨테이너 안에 등록되는 가짜 UserService (진짜 DB 없이 Controller만 검증)
    private UserService userService;

    @MockitoBean // SecurityConfig(JwtAuthenticationFilter)가 필요로 하는 의존성이라 같이 가짜로 준비해야 함
    private JwtTokenProvider jwtTokenProvider;

    // 이 테스트의 목적: 사용자 조회 응답에 password(암호화된 해시)가 새어 나가지 않는지 지키는 것
    // (실제로 UserController가 User 엔티티를 그대로 응답하던 시절, 응답에 password 해시가 포함되는 걸 직접 확인함)
    // /api/users/{id}는 SecurityConfig에서 ADMIN 전용이라 ADMIN으로 호출함
    @Test
    @WithMockUser(roles = "ADMIN") // "ADMIN 권한으로 로그인한 척" (roles에는 "ROLE_" 없이 쓰면 ROLE_ADMIN 권한이 만들어짐)
    void getUserById_성공이고_password는_응답에_없다() throws Exception {
        // given: password까지 채운 User를 준비. 엔티티에는 비밀번호가 있는데도 응답에는 안 나간다는 걸 보여야 하므로
        // 일부러 값을 넣음. findById가 호출되면 이 User를 돌려주도록 약속(stubbing)
        User user = new User();
        user.setId(1L);
        user.setLoginId("demotest");
        user.setPassword("encodedPassword");
        user.setName("데모테스트");
        user.setRole(UserRole.ADMIN);
        when(userService.findById(1L)).thenReturn(user);

        // when & then: 200 응답이고, 허용한 필드(loginId, name, role)는 있고, password는 응답에 "아예 없는지" 검증
        // doesNotExist(): 필드가 null로 오는 것과 달리, 필드 자체가 응답에 없을 때만 통과
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("demotest"))
                .andExpect(jsonPath("$.name").value("데모테스트"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser // 기본 역할은 USER (일반 사용자)
    void getUserById_일반사용자는_403() throws Exception {
        // when & then: 일반 사용자가 다른 사람의 정보를 조회하면 보안 필터 단계에서 막혀 403 + FORBIDDEN
        // 응답은 JwtAccessDeniedHandler가 만듦 (이 테스트가 그 핸들러를 처음으로 검증함)
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 서비스까지 들어가지 않았는지 확인: 403 응답만으로는 필터에서 막혔는지 컨트롤러 안에서 막혔는지 구분이 안 되고,
        // 안쪽까지 들어갔다면 규칙이 데이터 접근을 막지 못했다는 뜻이라 이 verify가 보안 규칙의 증거가 됨
        verify(userService, never()).findById(anyLong());
    }

    @Test
    @WithMockUser(username = "demotest") // 일반 사용자(USER)로 호출: /me는 로그인한 누구나 접근할 수 있어야 함
    void getMe_성공() throws Exception {
        // given: 토큰의 로그인 아이디("demotest")로 조회하면 이 User가 나오도록 약속
        // password를 일부러 채움: 엔티티에는 있어도 응답에는 안 나간다는 걸 보여야 하기 때문
        User user = new User();
        user.setId(3L);
        user.setLoginId("demotest");
        user.setPassword("encodedPassword");
        user.setName("데모테스트");
        user.setRole(UserRole.USER);
        when(userService.findByLoginId("demotest")).thenReturn(user);

        // when & then: /me가 /{id}가 아니라 getMe로 연결되는지(만약 /{id}로 갔다면 "me"를 Long으로 못 바꿔 400),
        // 본인 정보가 role과 함께 내려오고 password는 없는지 검증
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("demotest"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "ghost")
    void getMe_토큰은_유효한데_사용자가_없으면_401() throws Exception {
        // given: 토큰은 유효하지만 그 로그인 아이디의 사용자가 DB에 없는 경우(계정 삭제 등)
        // findByLoginId는 값을 돌려주는 메서드라 when(...).thenThrow(...)로 약속
        when(userService.findByLoginId("ghost")).thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        // when & then: 404가 아니라 401 + UNAUTHORIZED ("찾는 자원이 없다"가 아니라 "요청한 사람의 신원을 인정할 수 없다")
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

}
