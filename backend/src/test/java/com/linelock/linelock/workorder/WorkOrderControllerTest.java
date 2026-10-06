package com.linelock.linelock.workorder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.global.config.SecurityConfig;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.global.security.JwtTokenProvider;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(WorkOrderController.class) // Spring Boot 전체가 아니라 웹 계층(WorkOrderController + MVC 관련)만 가볍게 띄움
@Import(SecurityConfig.class) // 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
                              // (안 그러면 Spring Boot의 기본 보안(HTTP Basic, 401)이 대신 적용되어버림 -
                              // EquipmentControllerTest에서 겪은 문제)
public class WorkOrderControllerTest {

    @Autowired // 실제 HTTP 요청을 흉내 내는 도구를 Spring 컨테이너에서 주입받음
    private MockMvc mockMvc;

    @MockitoBean // Spring 컨테이너 안에 등록되는 가짜 WorkOrderService
    private WorkOrderService workOrderService;

    @MockitoBean // SecurityConfig(JwtAuthenticationFilter)가 필요로 하는 의존성이라 같이 가짜로 준비해야 함
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser // "로그인된 사용자인 척" - 없으면 Security에 막혀 Controller 로직 자체를 테스트 못 함
    void getWorkOrderById_성공() throws Exception {
        // given: 조회될 WorkOrder 준비 + findById(...)가 호출되면 그 WorkOrder를 찾았다고 가정(stubbing)
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(1L);
        workOrder.setDescription("테스트 작업");
        when(workOrderService.findById(1L)).thenReturn(workOrder);

        // when & then: GET 요청을 보내고, 상태코드와 응답 JSON의 description 필드값을 검증
        mockMvc.perform(get("/api/workorders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("테스트 작업"));
    }

    @Test
    @WithMockUser
    void createWorkOrder_성공() throws Exception {
        // given: 생성 요청으로 보낼 WorkOrder 준비 + save(...)가 호출되면 그대로 리턴하도록 가정(stubbing)
        WorkOrder workOrder = new WorkOrder();
        workOrder.setDescription("새 작업");

        when(workOrderService.save(any(WorkOrder.class))).thenReturn(workOrder);

        // when & then: POST 요청 본문에 WorkOrder를 JSON으로 담아 보내고, 응답을 검증
        // ObjectMapper.writeValueAsString(...) : 자바 객체 -> JSON 문자열로 변환 (Jackson 제공)
        // .contentType(MediaType.APPLICATION_JSON) : "이 요청 본문은 JSON이다"라고 명시
        mockMvc.perform(post("/api/workorders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(workOrder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("새 작업"));
    }

    @Test
    @WithMockUser
    void reserve_성공() throws Exception {
        // given: reserve(...)가 호출되면 이 WorkOrder를 리턴하도록 가정(stubbing)
        // anyLong()/any(WorkOrder.class) : 경로변수(equipmentId)와 본문(workOrder) 둘 다 "어떤
        // 값이든" 매칭
        WorkOrder workOrder = new WorkOrder();
        when(workOrderService.reserve(anyLong(), any(WorkOrder.class))).thenReturn(workOrder);

        // when & then: 경로변수(1)와 JSON 본문을 함께 담아 POST 요청, 200 응답인지 검증
        mockMvc.perform(post("/api/workorders/1/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(workOrder)))
                .andExpect(status().isOk());
    }

    @Test
    void getWorkOrderById_인증없으면_거부() throws Exception {
        // when & then: @WithMockUser 없이(=익명) 요청하면 보안 필터가 컨트롤러에 닿기 전에 막아 403이 뜨는지 검증
        // (GlobalExceptionHandler는 컨트롤러 이후에 터진 예외만 받으므로 이 403에는 영향이 없음)
        mockMvc.perform(get("/api/workorders/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void reserve_이미_사용중이라면_409() throws Exception {
        // given: reserve(...)가 호출되면 CustomException(EQUIPMENT_IN_USE)을 던지도록 약속
        // (값을 리턴하는 메서드라 when(...).thenThrow(...)를 쓸 수 있음)
        WorkOrder workOrder = new WorkOrder();
        when(workOrderService.reserve(anyLong(), any(WorkOrder.class)))
                .thenThrow(new CustomException(ErrorCode.EQUIPMENT_IN_USE));

        // when & then: 요청 형식은 올바른데 설비의 현재 상태와 충돌한 경우라 400이 아니라 409(Conflict)로 응답되는지 검증
        mockMvc.perform(post("/api/workorders/1/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(workOrder)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_IN_USE"));
    }

    @Test
    @WithMockUser
    void reserve_동시수정_충돌이면_409() throws Exception {
        // given: 낙관적 락(@Version) 충돌 예외를 던지도록 약속. 이 예외는 우리가 만든 CustomException이 아니라
        // Spring이 던지는 것이라, 실제 충돌 때 Hibernate가 채워주는 정보(엔티티 클래스, id)를 흉내 내서 생성함
        WorkOrder workOrder = new WorkOrder();
        when(workOrderService.reserve(anyLong(), any(WorkOrder.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Equipment.class, 1L));

        // when & then: CustomException용이 아닌 별도 핸들러가 받아서 409 + CONCURRENT_UPDATE_CONFLICT로 응답하는지 검증
        mockMvc.perform(post("/api/workorders/1/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(workOrder)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE_CONFLICT"));
    }
}
