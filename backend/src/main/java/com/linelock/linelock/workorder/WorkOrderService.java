package com.linelock.linelock.workorder;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.equipment.EquipmentStatus;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.user.User;
import com.linelock.linelock.user.UserRepository;
import com.linelock.linelock.user.UserRole;

import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드들을 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성 주입
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository; // WorkOrderRepository를 주입받음
    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음
    private final UserRepository userRepository; // UserRepository를 주입받음
    private final RedissonClient redissonClient; // Redis 분산락(RLock)을 얻기 위한 클라이언트

    // WorkOrder를 id로 조회. 없으면 WORK_ORDER_NOT_FOUND(404). 접근 권한은 검사하지 않는 순수 조회라서,
    // 사용자에게 보여줄 조회는 아래 findById(id, loginId)를 써야 함
    public WorkOrder findById(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    // 사용자가 요청한 WorkOrder 조회: 요청자 본인이거나 ADMIN만 볼 수 있음 (남의 작업지시를 id만 바꿔서 열어보는 것을 막음)
    // 이 검사를 SecurityConfig의 URL 규칙이 아니라 서비스에서 하는 이유: "누구의 작업지시인가"는 데이터를 읽어야 알 수 있어서
    // 호출자(loginId)는 토큰에서 꺼낸 값이라 위조할 수 없음
    // ADMIN 여부를 토큰의 role이 아니라 DB의 최신 값으로 확인함: 토큰은 발급 시점의 역할이라 강등된 뒤에도 만료까지 통하지만,
    // DB 값은 즉시 반영됨. 요청자 비교를 위해 호출자의 DB id가 어차피 필요해서 사용자 조회는 추가 비용이 없음
    public WorkOrder findById(Long id, String loginId) {
        // 토큰은 유효한데 호출자가 DB에 없는 경우(계정 삭제 등)는 404가 아니라 401: 신원을 인정할 수 없다는 뜻
        User caller = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        // 없는 작업지시는 권한 검사보다 먼저 404로 응답됨
        WorkOrder workOrder = findById(id);

        boolean isAdmin = caller.getRole() == UserRole.ADMIN;
        // 요청자는 문자열(loginId)이 아니라 id로 비교. requester가 null일 수 있는 예전 데이터(요청자 없이 저장된 것)는
        // 본인 것이 아니므로 NPE(500)가 나지 않게 null이면 비교하지 않음
        boolean isOwner = workOrder.getRequester() != null
                && workOrder.getRequester().getId().equals(caller.getId());

        // 남의 작업지시는 404(존재 자체를 숨김)가 아니라 403으로 응답: 존재 여부가 드러나는 것은 감수하는 선택
        // (id가 순차적이라 숨겨도 효과가 크지 않고, 403이어야 프론트가 "권한 없음"을 안내할 수 있음)
        // 존재 여부까지 숨겨야 하는 서비스라면 이 자리에서 WORK_ORDER_NOT_FOUND를 던지는 방식을 쓰기도 함
        if (!isAdmin && !isOwner) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return workOrder;
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
            Equipment equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND));
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

    // 컨트롤러가 호출하는 예약의 "입구". 요청 DTO를 엔티티로 조립한 뒤 위의 reserve(Long, WorkOrder)에 넘기는 역할만 함
    // 락 로직은 기존 메서드 한 곳에만 두고 이 입구 메서드는 락을 직접 다루지 않음 -> concurrencydemo의 /redis 엔드포인트가
    // 기존 시그니처를 그대로 쓰고 있어서, 시그니처를 바꾸는 대신 같은 이름의 메서드를 하나 더 둠(오버로딩: 파라미터가 달라서 구분됨)
    // 요청자는 요청 본문이 아니라 로그인한 사용자(loginId)로 서버가 직접 조회 -> 다른 사람 이름으로 예약하는 위조를 차단
    // 상태(CONFIRMED)는 여기서 정하지 않고 기존 reserve 안에서 서버가 정함
    public WorkOrder reserve(Long equipmentId, ReserveRequest request, String loginId) {
        // 토큰은 유효한데 그 사용자가 DB에 없는 경우(예: 토큰 발급 뒤 계정 삭제)는 404(USER_NOT_FOUND)가 아니라
        // "요청자의 신원을 인정할 수 없다"는 뜻의 401(UNAUTHORIZED). 락을 잡기 전에 일어나는 검사라 Redis는 건드리지 않음
        User requester = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

        // record의 값은 getDescription()이 아니라 description()처럼 필드 이름 그대로 꺼냄
        WorkOrder workOrder = new WorkOrder();
        workOrder.setDescription(request.description());
        workOrder.setStartTime(request.startTime());
        workOrder.setEndTime(request.endTime());
        workOrder.setRequester(requester);

        // 인자가 (Long, WorkOrder)라서 자기 자신이 아니라 기존 메서드(락 로직)가 호출됨
        return reserve(equipmentId, workOrder);
    }

}