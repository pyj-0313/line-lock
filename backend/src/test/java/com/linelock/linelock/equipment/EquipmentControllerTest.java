package com.linelock.linelock.equipment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import com.linelock.linelock.global.config.SecurityConfig;
import com.linelock.linelock.global.security.JwtTokenProvider;

@WebMvcTest(EquipmentController.class) // Spring Boot 전체가 아니라 웹 계층(Controller + MVC 관련)만 가볍게 띄움
@Import(SecurityConfig.class) // 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
                               // (안 그러면 Spring Boot의 기본 보안(HTTP Basic, 401)이 대신 적용되어버림 - 직접 겪은 문제)
public class EquipmentControllerTest {

    @Autowired // 실제 HTTP 요청을 흉내 내는 도구를 Spring 컨테이너에서 주입받음
    private MockMvc mockMvc;

    @MockitoBean // Spring 컨테이너 안에 등록되는 가짜 객체 (Mockito의 @Mock과 비슷하지만, Spring 빈으로 등록됨)
    private EquipmentService equipmentService;

    @MockitoBean // SecurityConfig(JwtAuthenticationFilter)가 필요로 하는 의존성이라 같이 가짜로 준비해야 함
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser // "로그인된 사용자인 척" 해주는 어노테이션 - 없으면 Security에 막혀 Controller 로직 자체를 테스트 못 함
    void getEquipmentById_성공() throws Exception {
        // given
        Equipment equipment = new Equipment();
        equipment.setId(1L);
        equipment.setEquipmentNumber("EQ-001");
        when(equipmentService.findById(1L)).thenReturn(equipment);

        // when & then: GET 요청을 보내고, 상태코드와 응답 JSON의 필드값을 검증
        mockMvc.perform(get("/api/equipments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentNumber").value("EQ-001"));
    }

    @Test
    void getEquipmentById_인증없으면_거부() throws Exception {
        // when & then: @WithMockUser 없이(=익명 사용자로) 요청하면, 진짜 SecurityConfig의
        // anyRequest().authenticated() 규칙에 걸려 403이 뜨는지 검증 (@Import(SecurityConfig.class) 덕분에 가능)
        mockMvc.perform(get("/api/equipments/1"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }
}
