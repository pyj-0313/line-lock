package com.linelock.linelock.workorder;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
import com.linelock.linelock.global.security.JwtAccessDeniedHandler;
import com.linelock.linelock.global.security.JwtAuthenticationEntryPoint;
import com.linelock.linelock.global.security.JwtTokenProvider;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(WorkOrderController.class) // Spring Boot 전체가 아니라 웹 계층(WorkOrderController + MVC 관련)만 가볍게 띄움
// 기본적으로는 로딩 안 되는 우리 진짜 SecurityConfig를 이 테스트에 끌어옴
// (안 그러면 Spring Boot의 기본 보안(HTTP Basic, 401)이 대신 적용되어버림 -
// EquipmentControllerTest에서 겪은 문제)
// SecurityConfig가 요구하는 401/403 핸들러(@Component)도 웹 계층 테스트에는 자동으로 안 올라와서 함께 가져옴 (빠지면 NoSuchBeanDefinitionException)
@Import({ SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class })
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
                // equipment를 반드시 채워야 함: 응답 DTO 변환(WorkOrderResponse.from)이 equipment.getId()를
                // 꺼내므로,
                // 비워두면 NullPointerException -> 마지막 보루 핸들러가 500으로 응답해 테스트가 실패함 (실제 DB 데이터는 설비가
                // 항상 있음)
                Equipment equipment = new Equipment();
                equipment.setId(10L);

                WorkOrder workOrder = new WorkOrder();
                workOrder.setId(1L);
                workOrder.setEquipment(equipment);
                workOrder.setDescription("테스트 작업");
                when(workOrderService.findById(1L)).thenReturn(workOrder);

                // when & then: GET 요청을 보내고, 상태코드와 응답 JSON의 필드값을 검증
                // 응답은 엔티티가 아니라 WorkOrderResponse(DTO)라서, 설비는 객체 통째가 아니라 equipmentId(숫자)로 나옴
                mockMvc.perform(get("/api/workorders/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.description").value("테스트 작업"))
                                .andExpect(jsonPath("$.equipmentId").value(10));
        }

        @Test
        @WithMockUser(username = "demotest")
        void reserve_성공() throws Exception {
                // given: 클라이언트가 보내는 예약 요청(ReserveRequest) 준비. 요청자나 상태는 본문에 없음 (서버가 정하는 값)
                // reserve(...)가 호출되면 저장된 결과(WorkOrder)를 리턴하도록 가정(stubbing)
                // anyLong()/any(ReserveRequest.class)/anyString() : 설비 id, 요청 본문, 로그인 아이디 셋 다
                // "어떤 값이든" 매칭
                ReserveRequest request = new ReserveRequest("점검 작업",
                                LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 12, 0));
                // 리턴할 WorkOrder에 equipment를 채워둠: 응답 DTO 변환(WorkOrderResponse.from)이
                // equipment.getId()를 꺼내므로 필요
                Equipment equipment = new Equipment();
                equipment.setId(1L);
                WorkOrder saved = new WorkOrder();
                saved.setId(100L);
                saved.setEquipment(equipment);
                saved.setStatus(WorkOrderStatus.CONFIRMED);
                when(workOrderService.reserve(anyLong(), any(ReserveRequest.class), anyString())).thenReturn(saved);

                // when & then: 경로변수(1)와 JSON 본문(ReserveRequest)을 함께 담아 POST 요청, 200 응답이고 상태가
                // CONFIRMED인지 검증
                // @WithMockUser(username = "demotest") : 컨트롤러가 principal.getName()으로 꺼낼 로그인
                // 아이디를 정해둠
                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        void getWorkOrderById_인증없으면_401() throws Exception {
                // when & then: @WithMockUser 없이(=익명) 요청하면 보안 필터가 컨트롤러에 닿기 전에 막아 401이 뜨는지 검증
                // 인증이 없으니 403(권한 부족)이 아니라 401(누군지 모름). 응답은 GlobalExceptionHandler가 아니라(컨트롤러 이후 예외만 받음)
                // JwtAuthenticationEntryPoint가 만들고, 본문도 다른 에러와 같은 ErrorResponse 형식이어야 해서 code까지 확인
                mockMvc.perform(get("/api/workorders/1"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @WithMockUser
        void reserve_이미_사용중이라면_409() throws Exception {
                // given: 예약 요청(ReserveRequest) 준비 + reserve(...)가 호출되면
                // CustomException(EQUIPMENT_IN_USE)을 던지도록 약속
                // 컨트롤러는 이제 새 메서드 reserve(설비 id, 요청 DTO, 로그인 아이디)를 부르므로 인자 3개를 모두 매처로 맞춰야 함
                // (옛 시그니처 reserve(Long, WorkOrder)로 약속하면 다른 메서드라서 약속이 안 먹고 NullPointerException
                // -> 500이 됨)
                // (값을 리턴하는 메서드라 when(...).thenThrow(...)를 쓸 수 있음)
                ReserveRequest request = new ReserveRequest("점검 작업",
                                LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 12, 0));

                when(workOrderService.reserve(anyLong(), any(ReserveRequest.class), anyString()))
                                .thenThrow(new CustomException(ErrorCode.EQUIPMENT_IN_USE));

                // when & then: 요청 형식은 올바른데 설비의 현재 상태와 충돌한 경우라 400이 아니라 409(Conflict)로 응답되는지 검증
                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("EQUIPMENT_IN_USE"));
        }

        @Test
        @WithMockUser
        void reserve_동시수정_충돌이면_409() throws Exception {
                // given: 예약 요청(ReserveRequest) 준비 + 낙관적 락(@Version) 충돌 예외를 던지도록 약속
                // 이 예외는 우리가 만든 CustomException이 아니라 Spring이 던지는 것이라, 실제 충돌 때 Hibernate가 채워주는
                // 정보(엔티티 클래스, id)를 흉내 내서 생성함. 인자 3개(설비 id, 요청 DTO, 로그인 아이디)를 매처로 맞추는 이유는 위 테스트와
                // 같음
                ReserveRequest request = new ReserveRequest("점검 작업",
                                LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 12, 0));
                when(workOrderService.reserve(anyLong(), any(ReserveRequest.class), anyString()))
                                .thenThrow(new ObjectOptimisticLockingFailureException(Equipment.class, 1L));

                // when & then: CustomException용이 아닌 별도 핸들러가 받아서 409 +
                // CONCURRENT_UPDATE_CONFLICT로 응답하는지 검증
                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE_CONFLICT"));
        }

        @Test
        @WithMockUser(username = "demotest")
        void reserve_요청자는_로그인한_사용자에서_온다() throws Exception {
                // 이 테스트의 목적: 요청자가 요청 본문이 아니라 "로그인한 사용자(토큰)"에서 온다는 보안 기능을 지키는 것
                // 누가 컨트롤러를 실수로 고쳐서 loginId를 다른 곳에서 가져와도, 이 테스트가 실패해서 잡아냄
                // (실제로 컨트롤러가 "someone-else"를 넘기게 일부러 바꿔서 이 테스트만 실패하는 것을 확인함)

                // given: 서비스가 아무 WorkOrder나 돌려주도록 약속. 이 테스트의 관심사는 응답이 아니라 "서비스에 뭘 넘겼는가"
                // equipment를 채우는 이유는 응답 DTO 변환(WorkOrderResponse.from)이 equipment.getId()를 꺼내서
                Equipment equipment = new Equipment();
                equipment.setId(1L);
                WorkOrder saved = new WorkOrder();
                saved.setEquipment(equipment);
                when(workOrderService.reserve(anyLong(), any(ReserveRequest.class),
                                anyString())).thenReturn(saved);

                ReserveRequest request = new ReserveRequest("점검 작업", LocalDateTime.of(2026, 10, 10, 10, 0),
                                LocalDateTime.of(2026, 10, 10, 12, 0));

                // when: @WithMockUser(username = "demotest")로 로그인한 사용자로서 예약 요청 (요청 본문에는 요청자 정보가
                // 없음)
                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isOk());

                // then: 컨트롤러가 서비스에 넘긴 로그인 아이디가 정확히 "demotest"인지 검증
                // eq(): "정확히 이 값". 인자 중 하나라도 매처(any, eq)를 쓰면 나머지도 전부 매처로 써야 하므로 1L도 eq(1L)로 감쌈
                verify(workOrderService).reserve(eq(1L), any(ReserveRequest.class), eq("demotest"));
        }

        @Test
        @WithMockUser
        void reserve_종료가_시작보다_앞이면_400() throws Exception {
                // 이 테스트의 목적: 두 필드의 관계 규칙(@AssertTrue isEndAfterStart)이 실제로 검사되는지 확인
                // (@WithMockUser: 검증은 컨트롤러 진입 직전 단계라, 로그인한 상태여야 보안 필터를 통과해 거기까지 도달함)
                // given: 내용은 정상이고 시간만 거꾸로(시작 12시, 종료 10시). 서비스는 stubbing하지 않음 - 검증에서 걸려 서비스까지
                // 가지 않기 때문
                ReserveRequest request = new ReserveRequest("점검 작업", LocalDateTime.of(2026, 10, 10, 12, 0),
                                LocalDateTime.of(2026, 10, 10, 10, 0));

                // when & then: 400 + INVALID_REQUEST이고, 틀린 건 하나뿐이라 필드 이름이 메서드 이름에서 나온
                // endAfterStart로 담김
                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                                .andExpect(jsonPath("$.fieldErrors[0].field").value("endAfterStart"))
                                .andExpect(jsonPath("$.fieldErrors[0].message").value("종료 시간은 시작 시간보다 뒤여야 합니다."));

                // 잘못된 요청은 서비스(락 로직)까지 가면 안 됨: 400이 나왔다는 것만으로는 이를 증명 못 해서 호출이 한 번도 없었는지 확인
                // (never(): 한 번도 호출되지 않았음을 검증. 인자 3개를 모두 매처로 맞춰야 하는 규칙은 동일)
                verify(workOrderService, never()).reserve(anyLong(), any(ReserveRequest.class), anyString());
        }

        @Test
        @WithMockUser
        void reserve_시작과_종료가_같으면_400() throws Exception {
                // 같은 시각(길이 0의 예약)도 거부해야 함: isAfter는 "엄격히 뒤"일 때만 true라서 걸림
                ReserveRequest request = new ReserveRequest("점검 작업", LocalDateTime.of(2026, 10, 10, 10, 0),
                                LocalDateTime.of(2026, 10, 10, 10, 0));

                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                                .andExpect(jsonPath("$.fieldErrors[0].field").value("endAfterStart"));
                verify(workOrderService, never()).reserve(anyLong(), any(ReserveRequest.class), anyString());
        }

        @Test
        @WithMockUser
        void reserve_내용이_비고_시간이_없으면_400() throws Exception {
                // 내용 빈 값 + 시작/종료 null: 각 필드 규칙(@NotBlank, @NotNull)이 자기 오류를 하나씩 내야 함
                ReserveRequest request = new ReserveRequest("", null, null);

                // when & then: 오류는 정확히 3개이고 endAfterStart는 없어야 함 - 시간이 null이면
                // isEndAfterStart()가 true를 돌려주고
                // 그 경우는 @NotNull이 따로 잡기 때문 (같은 원인으로 오류가 두 번 나지 않는다는 설계를 이 테스트가 고정함)
                // 오류 목록의 순서는 보장되지 않아서 containsInAnyOrder로 "순서 무관하게 이 값들"만 확인
                // (메시지 문자열은 ReserveRequest의 message와 한 글자도 다르면 안 됨)
                mockMvc.perform(post("/api/workorders/1/reserve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                                .andExpect(jsonPath("$.fieldErrors[*].field",
                                                containsInAnyOrder("description", "startTime", "endTime")))
                                .andExpect(jsonPath("$.fieldErrors[*].message",
                                                containsInAnyOrder("작업 내용을 입력해주세요.", "시작 시간을 입력해주세요.",
                                                                "종료 시간을 입력해주세요.")));
                verify(workOrderService, never()).reserve(anyLong(), any(ReserveRequest.class), anyString());
        }
}
