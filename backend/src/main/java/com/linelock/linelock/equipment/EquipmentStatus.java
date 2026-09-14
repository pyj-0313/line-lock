package com.linelock.linelock.equipment;

// 설비가 가질 수 있는 상태
public enum EquipmentStatus {
    RUNNING,     // 가동중 - 예약 불가
    IDLE,        // 대기 - 예약 가능
    MAINTENANCE  // 점검중 - 예약 불가
}
