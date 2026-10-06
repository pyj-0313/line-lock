package com.linelock.linelock.workorder;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.equipment.EquipmentStatus;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.user.UserRepository;

import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;

import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(workOrderRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성
                         // 주입
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository; // WorkOrderRepository를 주입받음
    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음
    private final UserRepository userRepository; // UserRepository를 주입받음
    private final RedissonClient redissonClient; // 

    public WorkOrder findById(Long id) { // WorkOrder 엔티티를 id로 조회하는 메서드
        return workOrderRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND)); // 없으면 CustomException(WORK_ORDER_NOT_FOUND) -> 핸들러가 404로 응답
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

    // 설비를 예약하는 메서드. Redis 분산락(RLock)으로 동시 접근을 막음 -> 동시성 로드맵 4단계(마지막)
    // 이 락은 서버가 여러 대여도 전부 같은 Redis를 바라보므로, DB 하나에만 의존하던 이전 단계들과 달리
    // "서버 프로세스가 여러 개"인 상황까지 대비한 방식임
    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
            // equipmentId별로 서로 다른 락 -> 설비 1번을 잠갔다고 설비 2번 예약까지 막히지 않음
            RLock lock = redissonClient.getLock("equipment-lock:" + equipmentId);
            boolean isLocked = false;
            try {
                // waitTime=5초: 이미 락을 쥔 다른 요청이 있으면 이만큼 대기해봄
                // leaseTime=3초: 락을 쥔 서버가 도중에 죽어도 이 시간이 지나면 자동으로 락이 풀림 (영구 잠김 방지)
                isLocked = lock.tryLock(5, 3, TimeUnit.SECONDS);
                if (!isLocked) {
                    // 5초를 기다려도 락을 못 얻음 = 같은 설비를 다른 요청이 계속 처리 중 -> 409로 응답
                    throw new CustomException(ErrorCode.LOCK_ACQUISITION_FAILED);
                }
                // 락을 획득한 시점부터는 이 equipmentId에 대해 전 세계에서 나 하나만 실행 중이라고 보장됨
                // -> 비관적/낙관적 락 때와 달리 DB 자체의 잠금이나 버전 체크에 기댈 필요가 없어짐
                Equipment equipment = equipmentRepository.findById(equipmentId).orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));
                if (equipment.getStatus() == EquipmentStatus.IDLE) {
                    equipment.setStatus(EquipmentStatus.RUNNING);
                    equipmentRepository.saveAndFlush(equipment);

                    workOrder.setEquipment(equipment);
                    workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
                    workOrder.setStatus(WorkOrderStatus.CONFIRMED);
                    return workOrderRepository.save(workOrder);
                }

                // 락을 얻고 확인해보니 이미 RUNNING -> 앞선 요청이 먼저 예약을 끝낸 것. 설비 현재 상태와 충돌이라 409로 응답
                throw new CustomException(ErrorCode.EQUIPMENT_IN_USE);
            } catch (InterruptedException e) {
                // 락 대기 중 스레드가 멈추라는 신호를 받은 경우. 신호를 다시 표시해두고(interrupt),
                // 사용자 잘못이 아닌 서버 사정이라 500으로 응답
                Thread.currentThread().interrupt();
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            } finally {
                if (isLocked) {
                    lock.unlock();
                }
            }
        }
     
}