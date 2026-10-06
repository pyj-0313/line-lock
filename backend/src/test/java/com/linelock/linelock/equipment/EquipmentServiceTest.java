package com.linelock.linelock.equipment;

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

import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class) // 이 테스트 클래스에서 Mockito(@Mock, @InjectMocks)를 쓸 수 있게 활성화
public class EquipmentServiceTest {

    @Mock // 진짜 DB 대신 가짜 EquipmentRepository 생성
    private EquipmentRepository equipmentRepository;

    @InjectMocks // 가짜 equipmentRepository를 주입받는, 진짜 테스트 대상
    private EquipmentService equipmentService;

    @Test
    void findById_성공() {
        // given: 조회될 Equipment 준비 + findById(...)가 호출되면 그 Equipment를 찾았다고 가정(stubbing)
        Equipment equipment = new Equipment();
        when(equipmentRepository.findById(anyLong())).thenReturn(Optional.of(equipment));

        // when: 실제 테스트 대상 메서드 실행
        Equipment result = equipmentService.findById(1L);

        // then: 리턴된 결과가 우리가 준비해둔 equipment와 같은 객체인지 검증
        assertThat(result).isEqualTo(equipment);
    }

    @Test
    void findById_없으면_예외() {
        // given: findById(...)가 호출되면 "조회 결과 없음"을 가정(stubbing)
        when(equipmentRepository.findById(anyLong())).thenReturn(Optional.empty());

        // when & then: findById() 내부의 orElseThrow가 CustomException(EQUIPMENT_NOT_FOUND)을 던지는지 검증
        CustomException e = assertThrows(CustomException.class, () -> equipmentService.findById(1L));
        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EQUIPMENT_NOT_FOUND);
    }

    @Test
    void save_성공() {
        // given: 저장할 Equipment 준비 + save(...)가 호출되면 그 Equipment를 그대로 리턴하도록 가정(stubbing)
        Equipment equipment = new Equipment();
        when(equipmentRepository.save(equipment)).thenReturn(equipment);

        // when: 실제 테스트 대상 메서드 실행
        equipmentService.save(equipment);

        // then: equipmentRepository.save()가 같은 equipment와 함께 실제로 호출됐는지 검증
        verify(equipmentRepository).save(equipment);
    }
}
