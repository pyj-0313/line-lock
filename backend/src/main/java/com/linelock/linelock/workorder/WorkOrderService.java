package com.linelock.linelock.workorder;

import org.springframework.stereotype.Service;

import com.linelock.linelock.equipment.EquipmentRepository;
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
}
