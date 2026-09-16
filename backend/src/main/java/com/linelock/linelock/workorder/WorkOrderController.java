package com.linelock.linelock.workorder;

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
    @GetMapping("/{id}")
    public WorkOrder getWorkOrderById(@PathVariable Long id) {
        return workOrderService.findById(id);
    }

    // POST /api/workorders : 새로운 WorkOrder 생성 (요청 본문의 JSON을 WorkOrder 객체로 변환해서 받음)
    @PostMapping
    public WorkOrder createWorkOrder(@RequestBody WorkOrder workOrder) {
        return workOrderService.save(workOrder);
    }

}
