package com.linelock.linelock.equipment;

import org.springframework.stereotype.Service;

import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(equipmentRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성 주입
public class EquipmentService {

    private final EquipmentRepository equipmentRepository; // EquipmentRepository를 주입받음

    public Equipment findById(Long id) { // Equipment 엔티티를 id로 조회하는 메서드
        return equipmentRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.EQUIPMENT_NOT_FOUND)); // 없으면 CustomException(EQUIPMENT_NOT_FOUND) -> 핸들러가 404로 응답
    }

    public Equipment save(Equipment equipment) { // Equipment 엔티티를 저장하는 메서드
        return equipmentRepository.save(equipment); // JpaRepository의 save 메서드를 사용하여 Equipment 엔티티를 저장
    }
}
