package com.linelock.linelock.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;
import com.linelock.linelock.user.User;
import com.linelock.linelock.user.UserRepository;


@ExtendWith(MockitoExtension.class) // 이 테스트 클래스에서 Mockito(@Mock, @InjectMocks)를 쓸 수 있게 활성화
public class WorkOrderServiceTest {

    @Mock // 진짜 DB 대신 가짜 WorkOrderRepository 생성
    private WorkOrderRepository workOrderRepository;

    @Mock // 진짜 DB 대신 가짜 EquipmentRepository 생성 (save()에서 getReferenceById 호출에 필요)
    private EquipmentRepository equipmentRepository;

    @Mock // 진짜 DB 대신 가짜 UserRepository 생성 (save()에서 getReferenceById 호출에 필요)
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
    void save_성공() {
        // given: equipment/requester의 id가 채워진 WorkOrder 준비
        // (save() 내부에서 workOrder.getEquipment().getId()를 호출하므로, equipment/requester가 null이면 NPE 발생)
        Equipment equipment = new Equipment();
        equipment.setId(1L);

        User requester = new User();
        requester.setId(1L);

        WorkOrder workOrder = new WorkOrder();
        workOrder.setEquipment(equipment);
        workOrder.setRequester(requester);

        // save() 내부에서 호출되는 3개 메서드 전부 stubbing
        when(equipmentRepository.getReferenceById(anyLong())).thenReturn(equipment);
        when(userRepository.getReferenceById(anyLong())).thenReturn(requester);
        when(workOrderRepository.save(workOrder)).thenReturn(workOrder);

        // when: 실제 테스트 대상 메서드 실행
        workOrderService.save(workOrder);

        // then: workOrderRepository.save()가 같은 workOrder와 함께 실제로 호출됐는지 검증
        verify(workOrderRepository).save(workOrder);
    }
}
