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

    // 설비를 예약하는 메서드. 지금은 의도적으로 락(lock)이 없음 -> 동시성 로드맵 1단계(버그 재현)용
    @Transactional // 이 메서드 전체를 하나의 트랜잭션으로 묶어서, equipment의 상태 변경이 트랜잭션 종료 시점에 자동으로 DB에 반영(dirty checking)되게 함
    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
        // [확인] 이 시점에 설비가 IDLE인지 읽음 -> 여기서 읽은 값과 실제 DB 값이 이후에 달라질 수 있는데, 그 틈을 막는 코드가 지금은 없음
        Equipment equipment = equipmentRepository.findById(equipmentId).orElseThrow();
        if(equipment.getStatus() == EquipmentStatus.IDLE){
            // [행동] 확인과 행동 사이에 다른 요청이 끼어들 수 있음 (check-then-act race condition)
            // 여러 요청이 동시에 여기 도달하면 전부 이 if를 통과해서, 같은 설비에 대해 WorkOrder가 여러 개 생성될 수 있음 -> 재현하려는 버그
            equipment.setStatus(EquipmentStatus.RUNNING);
            workOrder.setEquipment(equipment);
            workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
            workOrder.setStatus(WorkOrderStatus.CONFIRMED);
            return workOrderRepository.save(workOrder);
        }
        throw new IllegalStateException("이미 사용 중인 설비입니다.");
    }

}
