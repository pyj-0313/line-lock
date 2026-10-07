package com.linelock.linelock.workorder;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController // HTTP 요청을 받아 처리하고 반환값을 JSON으로 자동 변환해 응답하는 컨트롤러로 등록
@RequiredArgsConstructor // final 필드(workOrderService)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 의존성 주입
@RequestMapping("/api/workorders") // 이 클래스의 모든 API 주소는 /api/workorders로 시작
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    // GET /api/workorders/{id} : 특정 WorkOrder 조회
    // 엔티티가 아니라 WorkOrderResponse(DTO)로 응답 -> 연결된 Equipment(version)/User(password)의 내부 필드가 밖으로 나가지 않음
    // 엔티티 -> DTO 변환은 서비스가 아니라 컨트롤러에서 함 ("밖으로 내보낼 모양"은 API 입구의 몫)
    @GetMapping("/{id}")
    public WorkOrderResponse getWorkOrderById(@PathVariable Long id) {
        return WorkOrderResponse.from(workOrderService.findById(id));
    }

    // POST /api/workorders/{equipmentId}/reserve : 특정 설비를 예약
    // 값마다 "누가 정하는가"를 나눔: 설비는 URL 경로, 내용/시간은 요청 본문(ReserveRequest), 요청자는 로그인한 사용자(Principal)
    // 요청자를 본문이 아니라 토큰에서 꺼내므로 다른 사람 이름으로 예약하는 위조가 불가능하고, 상태(CONFIRMED)는 서버가 정함
    // Principal: 이 파라미터를 선언해두면 Spring MVC가 "지금 로그인한 사용자"를 넣어줌. getName()이 로그인 아이디
    // (@AuthenticationPrincipal String 대신 Principal을 쓴 이유: 운영과 테스트(@WithMockUser)의 principal 타입이 달라도 getName()은 같게 동작)
    // 참고: 예전에 있던 POST /api/workorders(엔티티를 그대로 저장)는 status를 클라이언트가 정해 락을 우회할 수 있어서 삭제함 -> 예약은 이 경로 하나뿐
    @PostMapping("/{equipmentId}/reserve")
    public WorkOrderResponse reserve(@PathVariable Long equipmentId,
            @RequestBody ReserveRequest request,
            Principal principal) {
        return WorkOrderResponse.from(workOrderService.reserve(equipmentId, request, principal.getName()));
    }

}
