package com.linelock.linelock.equipment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController // HTTP 요청을 받아 처리하고 반환값을 JSON으로 자동 변환해 응답하는 컨트롤러로 등록
@RequiredArgsConstructor // final 필드(equipmentService)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 의존성 주입
@RequestMapping("/api/equipments") // 이 클래스의 모든 API 주소는 /api/equipments로 시작
public class EquipmentController {

    private final EquipmentService equipmentService;

    // GET /api/equipments/{id} : id로 설비 하나 조회
    @GetMapping("/{id}")
    public Equipment getEquipmentById(@PathVariable Long id) {
        return equipmentService.findById(id);
    }
}
