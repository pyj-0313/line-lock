package com.linelock.linelock.workorder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.equipment.EquipmentStatus;
import com.linelock.linelock.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(workOrderRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성 주입
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository; // WorkOrderRepository를 주입받음
    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음
    private final UserRepository userRepository; // UserRepository를 주입받음
    
    public WorkOrder findById(Long id) { // WorkOrder 엔티티를 id로 조회하는 메서드
        return workOrderRepository.findById(id).orElseThrow(); // Optional이라 없으면 예외 발생시킴
    }

    public WorkOrder save(WorkOrder workOrder) { // WorkOrder 엔티티를 저장하는 메서드

        workOrder.setEquipment(equipmentRepository.getReferenceById(workOrder.getEquipment().getId())); // WorkOrder에 설정된 Equipment의 id로 실제 Equipment 엔티티를 조회하여 설정

        workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId())); // WorkOrder에 설정된 User의 id로 실제 User 엔티티를 조회하여 설정

        return workOrderRepository.save(workOrder); // JpaRepository의 save 메서드를 사용하여 WorkOrder 엔티티를 저장
    }

    // 설비를 예약하는 메서드. 비관적 락(PESSIMISTIC_WRITE)으로 동시 접근을 막음 -> 동시성 로드맵 2단계
    @Transactional // 이 메서드 전체를 하나의 트랜잭션으로 묶어서, equipment의 상태 변경이 트랜잭션 종료 시점에 자동으로 DB에 반영(dirty checking)되고, 락도 트랜잭션이 끝날 때까지 유지되게 함
    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
        // [확인] findWithLockById가 SELECT ... FOR UPDATE를 실행 -> 이 행에 배타 잠금(exclusive lock)을 걸어서
        // 이 트랜잭션이 끝날 때까지 다른 트랜잭션은 이 행을 읽는 것조차 대기하게 만듦 (락 없음 단계처럼 동시에 IDLE을 읽어버리는 틈이 없어짐)
        Equipment equipment = equipmentRepository.findWithLockById(equipmentId).orElseThrow();
        if(equipment.getStatus() == EquipmentStatus.IDLE){
            // [행동] 이미 배타 잠금을 쥐고 있으므로, 뒤이어 대기 중이던 다른 트랜잭션은 이 트랜잭션이 커밋된 뒤에야
            // RUNNING으로 바뀐 최신 상태를 읽게 되어 정상적으로 else 분기(거부)로 빠짐 -> 이중예약도, 데드락도 발생하지 않음
            equipment.setStatus(EquipmentStatus.RUNNING);
            // 이미 이 트랜잭션이 equipment에 배타 잠금을 쥐고 있어서 순서를 바꿀 필요는 없지만,
            // 락 없음 단계와 동일한 흐름을 유지하기 위해 saveAndFlush 그대로 둠
            equipmentRepository.saveAndFlush(equipment);

            workOrder.setEquipment(equipment);
            workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
            workOrder.setStatus(WorkOrderStatus.CONFIRMED);
            return workOrderRepository.save(workOrder);
        }
        throw new IllegalStateException("이미 사용 중인 설비입니다.");
    }

}
