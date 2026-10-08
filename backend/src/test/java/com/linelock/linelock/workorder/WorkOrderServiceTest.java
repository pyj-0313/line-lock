package com.linelock.linelock.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.linelock.linelock.user.User;
import com.linelock.linelock.user.UserRepository;
import com.linelock.linelock.user.UserRole;

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

        // when & then: findById() 내부의 orElseThrow가
        // CustomException(WORK_ORDER_NOT_FOUND)을 던지는지 검증
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

    // 테스트 데이터를 만드는 도우미: 사용자 한 명을 id, 로그인 아이디, 역할을 채워서 생성
    private User createUser(Long id, String loginId, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setLoginId(loginId);
        user.setRole(role);
        return user;
    }

    @Test
    void findById_본인의_작업지시면_성공() {
        // given: 호출자(owner)가 요청자인 작업지시
        User owner = createUser(1L, "owner", UserRole.USER);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setRequester(owner);
        when(userRepository.findByLoginId("owner")).thenReturn(Optional.of(owner));
        when(workOrderRepository.findById(10L)).thenReturn(Optional.of(workOrder));

        // when & then: 본인 것이라 그대로 조회됨
        assertThat(workOrderService.findById(10L, "owner")).isEqualTo(workOrder);
    }

    @Test
    void findById_남의_작업지시를_일반사용자가_조회하면_403() {
        // given: 요청자(owner, id=1)와 다른 일반 사용자(other, id=2)가 호출
        User owner = createUser(1L, "owner", UserRole.USER);
        User other = createUser(2L, "other", UserRole.USER);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setRequester(owner);
        when(userRepository.findByLoginId("other")).thenReturn(Optional.of(other));
        when(workOrderRepository.findById(10L)).thenReturn(Optional.of(workOrder));

        // when & then: 본인도 ADMIN도 아니므로 FORBIDDEN
        CustomException e = assertThrows(CustomException.class, () -> workOrderService.findById(10L, "other"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void findById_ADMIN은_남의_작업지시도_조회() {
        // given: 요청자는 owner이고 호출자는 ADMIN(admin, id=3). 요청자가 admin 본인이면 "본인 것"이라서 통과하므로
        // ADMIN 권한 덕분에 통과하는지 증명하려면 요청자를 반드시 다른 사람(owner)으로 둬야 함
        User owner = createUser(1L, "owner", UserRole.USER);
        User admin = createUser(3L, "admin", UserRole.ADMIN);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setRequester(owner);
        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        when(workOrderRepository.findById(10L)).thenReturn(Optional.of(workOrder));

        // when & then: 본인 것이 아니어도 ADMIN이라 조회됨
        assertThat(workOrderService.findById(10L, "admin")).isEqualTo(workOrder);
    }

    @Test
    void findById_호출자가_DB에_없으면_401() {
        // given: 토큰은 유효하지만 호출자가 DB에 없음 (계정 삭제 등)
        when(userRepository.findByLoginId("ghost")).thenReturn(Optional.empty());

        // when & then: 404(USER_NOT_FOUND)가 아니라 401(UNAUTHORIZED)이고,
        // 호출자 확인이 먼저라서 작업지시는 조회하지도 않아야 함 (never)
        CustomException e = assertThrows(CustomException.class, () -> workOrderService.findById(10L, "ghost"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(workOrderRepository, never()).findById(anyLong());
    }

    @Test
    void findById_작업지시가_없으면_404() {
        // given: 호출자는 정상이지만 작업지시가 없음
        User caller = createUser(1L, "owner", UserRole.USER);
        when(userRepository.findByLoginId("owner")).thenReturn(Optional.of(caller));
        when(workOrderRepository.findById(10L)).thenReturn(Optional.empty());

        // when & then: 권한 검사에 앞서 WORK_ORDER_NOT_FOUND
        CustomException e = assertThrows(CustomException.class, () -> workOrderService.findById(10L, "owner"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.WORK_ORDER_NOT_FOUND);
    }

    @Test
    void findById_요청자가_없는_예전_작업지시는_일반사용자에게_403() {
        // given: 요청자 없이 저장된 예전 데이터(requester가 null)
        User caller = createUser(1L, "owner", UserRole.USER);
        WorkOrder workOrder = new WorkOrder(); // requester를 일부러 비워둠
        when(userRepository.findByLoginId("owner")).thenReturn(Optional.of(caller));
        when(workOrderRepository.findById(10L)).thenReturn(Optional.of(workOrder));

        // when & then: NullPointerException(500)이 아니라 본인 것이 아니므로 FORBIDDEN
        CustomException e = assertThrows(CustomException.class, () -> workOrderService.findById(10L, "owner"));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

}
