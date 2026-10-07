package com.linelock.linelock.workorder;

import java.time.LocalDateTime;

// 작업지시 응답 전용 객체(DTO). 엔티티를 그대로 응답하면 연결된 Equipment(version 등 내부 필드)와
// User(password 해시)까지 딸려 나갈 수 있어서, 내보내도 되는 값만 골라 담는다
// 설비는 엔티티 통째가 아니라 id만(equipmentId) 내보내 내부 필드가 다시 새지 않게 함
public record WorkOrderResponse(Long id, Long equipmentId, String description, LocalDateTime startTime,
        LocalDateTime endTime, WorkOrderStatus status) {

    // WorkOrder 엔티티 -> 응답 DTO 변환. 새 객체를 만드는 메서드라 static
    // new 안의 값 순서는 위 record 선언의 필드 순서와 같아야 함 (startTime/endTime처럼 타입이 같으면 바뀌어도 컴파일이 되므로 주의)
    // id를 long(기본형)이 아닌 Long으로 둔 이유: 기본형은 null을 못 가져서, id가 null인 경우 변환 시 NullPointerException이 남
    public static WorkOrderResponse from(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getEquipment().getId(),
                workOrder.getDescription(),
                workOrder.getStartTime(),
                workOrder.getEndTime(),
                workOrder.getStatus());
    }
}
