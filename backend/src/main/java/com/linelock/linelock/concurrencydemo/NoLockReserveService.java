package com.linelock.linelock.concurrencydemo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.equipment.EquipmentStatus;
import com.linelock.linelock.user.UserRepository;
import com.linelock.linelock.workorder.WorkOrder;
import com.linelock.linelock.workorder.WorkOrderRepository;
import com.linelock.linelock.workorder.WorkOrderStatus;

import lombok.RequiredArgsConstructor;

// 동시성 로드맵 4단계 비교용 데모 코드. 실제 프로덕션 예약 로직은 WorkOrderService(Redis 분산락)에 있고,
// 이 클래스는 과거 "락 없음" 단계의 구현을 git 히스토리에서 복원해 부하테스트 비교 대상으로만 남겨둔 것임
@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(workOrderRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성 주입
public class NoLockReserveService {

    private final WorkOrderRepository workOrderRepository; // WorkOrderRepository를 주입받음
    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음
    private final UserRepository userRepository; // UserRepository를 주입받음

    // 설비를 예약하는 메서드. 지금은 의도적으로 락(lock)이 없음 -> 동시성 로드맵 1단계(버그 재현)용
    @Transactional // 이 메서드 전체를 하나의 트랜잭션으로 묶어서, equipment의 상태 변경이 트랜잭션 종료 시점에 자동으로 DB에 반영(dirty checking)되게 함
    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
        // [확인] 이 시점에 설비가 IDLE인지 읽음 -> 여기서 읽은 값과 실제 DB 값이 이후에 달라질 수 있는데, 그 틈을 막는 코드가 지금은 없음
        Equipment equipment = equipmentRepository.findById(equipmentId).orElseThrow();
        if(equipment.getStatus() == EquipmentStatus.IDLE){
            // [행동] 확인과 행동 사이에 다른 요청이 끼어들 수 있음 (check-then-act race condition)
            // 여러 요청이 동시에 여기 도달하면 전부 이 if를 통과해서, 같은 설비에 대해 WorkOrder가 여러 개 생성될 수 있음 -> 재현하려는 버그
            equipment.setStatus(EquipmentStatus.RUNNING);
            // saveAndFlush로 WorkOrder 저장보다 먼저, 즉시 UPDATE를 내보냄.
            // WorkOrder INSERT(FK 참조 확인용 공유 잠금)와 equipment UPDATE(배타 잠금)가 뒤섞이는 순서로 두면
            // 동시 요청 시 서로의 잠금 해제를 기다리다 MySQL 데드락이 발생함(직접 재현해서 확인함) -> 순서를 명시적으로 고정
            equipmentRepository.saveAndFlush(equipment);

            workOrder.setEquipment(equipment);
            workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
            workOrder.setStatus(WorkOrderStatus.CONFIRMED);
            return workOrderRepository.save(workOrder);
        }
        throw new IllegalStateException("이미 사용 중인 설비입니다.");
    }

}
