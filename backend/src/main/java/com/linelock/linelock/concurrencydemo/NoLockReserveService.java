package com.linelock.linelock.concurrencydemo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
// demo 프로필에서만 빈으로 등록됨 (이유와 규칙은 package-info.java 참고)
@Profile("demo")
public class NoLockReserveService {

    private final WorkOrderRepository workOrderRepository; // WorkOrderRepository를 주입받음
    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음
    private final UserRepository userRepository; // UserRepository를 주입받음

    // 설비를 예약하는 메서드. 지금은 의도적으로 락(lock)이 없음 -> 동시성 로드맵 1단계(버그 재현)용
    // @Transactional을 일부러 안 붙임(참고: spring.jpa.open-in-view가 기본 켜져 있어서, 이 메서드에
    // @Transactional이 없어도 equipment는 요청이 끝날 때까지 여전히 Hibernate 감시 대상(managed)으로 남음).
    // 그래서 이 equipment 객체의 필드를 직접 setStatus()로 바꾸지 않음 -> 바꾸면 나중에 bulk update 실행
    // 직전 Hibernate의 자동 flush가 그 변경분을 먼저 버전 체크가 붙은 UPDATE로 반영해버려서 아무 소용이 없어짐
    // (직접 겪은 문제). DB 반영은 오직 아래 updateStatusIgnoringVersion() 한 줄이 담당함
    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
        // [확인] 이 시점에 설비가 IDLE인지 읽음 -> 여기서 읽은 값과 실제 DB 값이 이후에 달라질 수 있는데, 그 틈을 막는 코드가 지금은 없음
        Equipment equipment = equipmentRepository.findById(equipmentId).orElseThrow();
        if(equipment.getStatus() == EquipmentStatus.IDLE){
            // [행동] 확인과 행동 사이에 다른 요청이 끼어들 수 있음 (check-then-act race condition)
            // equipment 엔티티에는 여전히 @Version이 걸려있어서, save/saveAndFlush로 저장하면 Hibernate가
            // 자동으로 버전 체크(WHERE version=?)를 끼워 넣어버려 "락 없음"이 성립하지 않음(직접 겪은 문제).
            // 그래서 엔티티를 거치지 않고 JPQL bulk update로 DB에 직접 SQL을 날려 버전 체크 자체를 우회함
            equipmentRepository.updateStatusIgnoringVersion(equipmentId, EquipmentStatus.RUNNING);

            workOrder.setEquipment(equipment);
            workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
            workOrder.setStatus(WorkOrderStatus.CONFIRMED);
            return workOrderRepository.save(workOrder);
        }
        throw new IllegalStateException("이미 사용 중인 설비입니다.");
    }

}
