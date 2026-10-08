package com.linelock.linelock.concurrencydemo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linelock.linelock.workorder.WorkOrder;
import com.linelock.linelock.workorder.WorkOrderService;

import lombok.RequiredArgsConstructor;

// 동시성 제어 5가지 방식(락없음/메모리락/비관적/낙관적/Redis)을 한 서버에서 동시에 비교하기 위한 데모 전용 컨트롤러.
// 실제 서비스가 쓰는 프로덕션 엔드포인트는 /api/workorders/{id}/reserve(WorkOrderService, Redis 분산락) 이고,
// 여기 5개 엔드포인트는 k6 부하테스트로 방식별 처리량/실패율/응답시간을 비교하려고 별도로 마련한 것임
// (메모리락은 서버 1대에서는 정상이지만 서버가 여러 대면 서버마다 따로 통과시켜 깨진다는 걸 보여주는 용도)
// demo 프로필에서만 등록됨: 기본 실행에서는 이 주소들이 존재하지 않음 (이유는 package-info.java 참고)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo/reserve")
// demo 프로필에서만 빈으로 등록됨 (이유와 규칙은 package-info.java 참고)
@Profile("demo")
public class ConcurrencyDemoController {

    private final NoLockReserveService noLockReserveService;
    private final OptimisticReserveService optimisticReserveService;
    private final PessimisticReserveService pessimisticReserveService;
    private final WorkOrderService workOrderService;
    private final MemoryLockReserveService memoryLockReserveService;

    @PostMapping("/no-lock/{equipmentId}")
    public WorkOrder nolock(@PathVariable Long equipmentId, @RequestBody WorkOrder workOrder) {
        return noLockReserveService.reserve(equipmentId, workOrder);
    }

    @PostMapping("/pessimistic/{equipmentId}")
    public WorkOrder pessimistic(@PathVariable Long equipmentId, @RequestBody WorkOrder workOrder ) {
        return pessimisticReserveService.reserve(equipmentId, workOrder);
    }

    @PostMapping("/optimistic/{equipmentId}")
    public WorkOrder optimistic(@PathVariable Long equipmentId, @RequestBody WorkOrder workOrder) {
        return optimisticReserveService.reserve(equipmentId, workOrder);
    }

    @PostMapping("/redis/{equipmentId}")
    public WorkOrder workOrder(@PathVariable Long equipmentId, @RequestBody WorkOrder workOrder) {
        return workOrderService.reserve(equipmentId, workOrder);
    }

    @PostMapping("/memory-lock/{equipmentId}")
    public WorkOrder memoryLock(@PathVariable Long equipmentId, @RequestBody WorkOrder workOrder) {
        return memoryLockReserveService.reserve(equipmentId, workOrder);
    }
}
