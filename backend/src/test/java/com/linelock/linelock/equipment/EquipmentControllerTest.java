package com.linelock.linelock.equipment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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
        // anyRequest().authenticated() 규칙에 걸려 403이 뜨는지 검증
        // (@Import(SecurityConfig.class) 덕분에 가능)
        mockMvc.perform(get("/api/equipments/1"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getEquipmentById_없으면_404() throws Exception {
        // given: findById(999)가 호출되면 CustomException(EQUIPMENT_NOT_FOUND)을 던지도록 약속
        // thenThrow : "이 값을 리턴해라"(thenReturn)와 달리 "이 예외를 던져라"를 약속하는 문법
        when(equipmentService.findById(999L))
                .thenThrow(new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));

        // when & then: 서비스가 던진 예외를 GlobalExceptionHandler가 받아서 404 + 응답 JSON으로 바꿔주는지 검증
        // status()는 응답 상태코드를, jsonPath()는 응답 본문(JSON)의 필드값을 확인함
        mockMvc.perform(get("/api/equipments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void 예상못한_예외면_500이고_내부정보는_숨긴다() throws Exception {
        // given: 서비스에서 예상 못한 예외(버그)가 터진 상황. 예외 메시지에 내부 정보가 들어 있다고 가정
        // (password 문자열은 테스트용 가짜 값. 진짜 비밀이 아니라 "새어 나가는지" 확인하려고 넣은 미끼)
        when(equipmentService.findById(1L))
                .thenThrow(new RuntimeException("DB 접속 정보 password=secret1234"));

        // when & then: 마지막 보루인 Exception 핸들러가 받아서 500 + 정리된 메시지로 응답하는지 검증
        // message가 예외의 원래 메시지가 아니라 고정 문구여야 "내부 정보는 응답에 노출하지 않고 로그에만 남긴다"가 지켜진 것
        mockMvc.perform(get("/api/equipments/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @Test
    @WithMockUser
    void 존재하지않는_경로면_404() throws Exception {
        // when & then: 어떤 컨트롤러에도 없는 경로를 요청하면 Spring이 NoResourceFoundException을 던짐
        // 이걸 전용 핸들러가 받아 404 + RESOURCE_NOT_FOUND로 응답하는지 검증
        // (전용 핸들러가 없으면 마지막 보루인 Exception 핸들러가 가로채 500이 되어버림 - 실제로 겪은 문제를 막는 테스트)
        mockMvc.perform(get("/api/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void 설비id에_문자를_넣으면_400() throws Exception {
        // when & then: Long을 기대하는 id 자리에 "abc"를 넣으면 서비스에 닿기도 전에 Spring이 타입 변환에 실패함
        // 그래서 서비스를 stubbing(when)할 필요가 없음. 클라이언트 잘못이라 500이 아니라 400 + INVALID_REQUEST로 응답되는지 검증
        mockMvc.perform(get("/api/equipments/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
