package com.linelock.linelock.user;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linelock.linelock.global.config.SecurityConfig;
import com.linelock.linelock.global.security.JwtAccessDeniedHandler;
import com.linelock.linelock.global.security.JwtAuthenticationEntryPoint;
import com.linelock.linelock.global.security.JwtTokenProvider;

@WebMvcTest(UserController.class) // Spring Boot 전체가 아니라 웹 계층(UserController + MVC 관련)만 가볍게 띄움
// 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
// SecurityConfig가 요구하는 401/403 핸들러(@Component)도 웹 계층 테스트에는 자동으로 안 올라와서 함께 가져옴 (빠지면 NoSuchBeanDefinitionException)
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
    @Test
    @WithMockUser // "로그인된 사용자인 척" - 없으면 Security에 막혀 Controller 로직 자체를 테스트 못 함
    void getUserById_성공이고_password는_응답에_없다() throws Exception {
        // given: password까지 채운 User를 준비. 엔티티에는 비밀번호가 있는데도 응답에는 안 나간다는 걸 보여야 하므로
        // 일부러 값을 넣음. findById가 호출되면 이 User를 돌려주도록 약속(stubbing)
        User user = new User();
        user.setId(1L);
        user.setLoginId("demotest");
        user.setPassword("encodedPassword");
        user.setName("데모테스트");
        when(userService.findById(1L)).thenReturn(user);

        // when & then: 200 응답이고, 허용한 필드(loginId, name)는 있고, password는 응답에 "아예 없는지" 검증
        // doesNotExist(): 필드가 null로 오는 것과 달리, 필드 자체가 응답에 없을 때만 통과
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("demotest"))
                .andExpect(jsonPath("$.name").value("데모테스트"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

}
