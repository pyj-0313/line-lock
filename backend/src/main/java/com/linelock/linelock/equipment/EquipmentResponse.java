package com.linelock.linelock.equipment;

// 설비 조회 API의 응답 전용 객체(DTO). version은 낙관적 락(@Version)을 위한 내부 구현 필드라서 일부러 뺐다
// (API 사용자가 알 필요가 없고, 노출하면 내부 동시성 설계가 드러남). 나중에 프론트가 수정 요청에 낙관적 락을 쓰게 되면 그때 포함 여부를 고민
public record EquipmentResponse(Long id, String equipmentNumber, EquipmentStatus status) {

    // Equipment 엔티티 -> 응답 DTO 변환. 객체를 만드는 메서드라 static (UserResponse.from과 같은 패턴)
    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(equipment.getId(), equipment.getEquipmentNumber(), equipment.getStatus());
    }
    
}
