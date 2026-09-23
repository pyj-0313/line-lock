package com.linelock.linelock.workorder;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.equipment.EquipmentStatus;
import com.linelock.linelock.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(workOrderRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성
                         // 주입
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository; // WorkOrderRepository를 주입받음
    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음
    private final UserRepository userRepository; // UserRepository를 주입받음

    public WorkOrder findById(Long id) { // WorkOrder 엔티티를 id로 조회하는 메서드
        return workOrderRepository.findById(id).orElseThrow(); // Optional이라 없으면 예외 발생시킴
    }

    public WorkOrder save(WorkOrder workOrder) { // WorkOrder 엔티티를 저장하는 메서드

        workOrder.setEquipment(equipmentRepository.getReferenceById(workOrder.getEquipment().getId())); // WorkOrder에
                                                                                                        // 설정된
                                                                                                        // Equipment의
                                                                                                        // id로 실제
                                                                                                        // Equipment
                                                                                                        // 엔티티를 조회하여 설정

        workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId())); // WorkOrder에 설정된
                                                                                                   // User의 id로 실제 User
                                                                                                   // 엔티티를 조회하여 설정

        return workOrderRepository.save(workOrder); // JpaRepository의 save 메서드를 사용하여 WorkOrder 엔티티를 저장
    }

    // 설비를 예약하는 메서드. 낙관적 락(@Version)으로 동시 접근을 막음 -> 동시성 로드맵 3단계
    // @Transactional을 일부러 안 붙임: 충돌로 실패한 트랜잭션을 이어서 재사용하면 위험하므로,
    // 재시도마다 findById/saveAndFlush/save 각각이 자기 자신만의 새 트랜잭션으로 독립 실행되게 함
    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
        int maxRetries = 5; // 충돌이 나도 이 횟수까지는 자동으로 다시 시도해봄
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // [확인] 락 없이 그냥 읽음(SELECT). 이 값이 저장 시점엔 낡아있을 수 있는데,
                // 그 충돌 여부는 Equipment의 @Version이 UPDATE 시점에 자동으로 검사해줌
                Equipment equipment = equipmentRepository.findById(equipmentId).orElseThrow();
                if (equipment.getStatus() == EquipmentStatus.IDLE) {
                    equipment.setStatus(EquipmentStatus.RUNNING);
                    // saveAndFlush가 즉시 UPDATE를 내보내는 순간, Hibernate가 자동으로 WHERE version=?을 붙임.
                    // 그 사이 다른 트랜잭션이 먼저 커밋해서 버전이 바뀌었다면 여기서 ObjectOptimisticLockingFailureException 발생
                    equipmentRepository.saveAndFlush(equipment);

                    workOrder.setEquipment(equipment);
                    workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
                    workOrder.setStatus(WorkOrderStatus.CONFIRMED);
                    return workOrderRepository.save(workOrder);
                }

                throw new IllegalStateException("이미 사용 중인 설비입니다.");
            } catch (ObjectOptimisticLockingFailureException e) {
                // 버전 충돌 = 내가 읽은 정보가 낡았다는 뜻일 뿐, 진짜 실패인지는 아직 모름
                // -> 아무것도 안 하고 그냥 넘어가면 for문이 다음 시도에서 equipment를 다시 최신 상태로 읽어옴
            }
        }
        // maxRetries번을 다 써도 계속 충돌났다는 뜻 -> 그만 포기하고 실패 처리
        throw new IllegalStateException("여러 번 재시도했지만 예약에 실패했습니다.");
    }
}