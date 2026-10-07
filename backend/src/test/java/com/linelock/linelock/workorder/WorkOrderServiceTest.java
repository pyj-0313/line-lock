package com.linelock.linelock.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.user.UserRepository;


@ExtendWith(MockitoExtension.class) // 이 테스트 클래스에서 Mockito(@Mock, @InjectMocks)를 쓸 수 있게 활성화
public class WorkOrderServiceTest {

    @Mock // 진짜 DB 대신 가짜 WorkOrderRepository 생성
    private WorkOrderRepository workOrderRepository;

    @Mock // reserve()에서만 쓰이지만, WorkOrderService 생성자가 요구하는 의존성이라 같이 가짜로 만들어줘야 함
    private EquipmentRepository equipmentRepository;

    @Mock // 진짜 DB 대신 가짜 UserRepository 생성 (예약 입구 메서드가 loginId로 요청자를 조회할 때 필요)
    private UserRepository userRepository;

    @Mock // reserve()에서만 쓰이지만, WorkOrderService 생성자가 요구하는 의존성이라 같이 가짜로 만들어줘야 함
    private RedissonClient redissonClient;

    @InjectMocks // 위 4개의 가짜 객체들을 실제로 주입받는, 우리가 진짜로 테스트하려는 대상
    private WorkOrderService workOrderService;

    @Test
    void findById_성공() {
        // given: 조회될 WorkOrder 준비 + findById(...)가 호출되면 그 WorkOrder를 찾았다고 가정(stubbing)
        WorkOrder workOrder = new WorkOrder();
        when(workOrderRepository.findById(anyLong())).thenReturn(Optional.of(workOrder));

        // when: 실제 테스트 대상 메서드 실행
        WorkOrder result = workOrderService.findById(1L);

        // then: 리턴된 결과가 우리가 준비해둔 workOrder와 같은 객체인지 검증
        assertThat(result).isEqualTo(workOrder);
    }

    @Test
    void findById_없으면_예외() {
        // given: findById(...)가 호출되면 "조회 결과 없음"을 가정(stubbing)
        when(workOrderRepository.findById(anyLong())).thenReturn(Optional.empty());

        // when & then: findById() 내부의 orElseThrow가 CustomException(WORK_ORDER_NOT_FOUND)을 던지는지 검증
        CustomException e = assertThrows(CustomException.class, () -> workOrderService.findById(1L));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WORK_ORDER_NOT_FOUND);
    }

    @Test
    void reserve_토큰의_사용자가_DB에_없으면_401() {
        // given: 토큰은 유효하지만 그 loginId의 사용자가 DB에 없는 상황(예: 토큰 발급 뒤 계정이 삭제됨)을 가정(stubbing)
        when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty());
        ReserveRequest request = new ReserveRequest("점검 작업",
                LocalDateTime.of(2026, 10, 10, 10, 0), LocalDateTime.of(2026, 10, 10, 12, 0));

        // when & then: 요청자 조회가 락을 잡기 전에 일어나므로 Redisson을 stubbing할 필요가 없음
        // "요청자의 신원을 인정할 수 없다"는 상황이라 404(USER_NOT_FOUND)가 아니라 401(UNAUTHORIZED)인지 검증
        CustomException e = assertThrows(CustomException.class, () -> workOrderService.reserve(1L, request, "ghost"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
