package com.linelock.linelock.workorder;

// 작업지시(예약 요청)가 처리 과정에서 거치는 상태
public enum WorkOrderStatus {
    PENDING,    // 대기 - 예약 요청이 접수되어 락 획득 처리를 기다리는 중
    CONFIRMED,  // 확정 - 락을 획득해서 예약이 성사된 상태
    FAILED,     // 실패 - 동시성 경쟁에서 밀려 예약에 실패한 상태 (부하테스트에서 집계할 지표)
    COMPLETED   // 완료 - 예약된 사용 시간이 끝나 작업이 마무리된 상태
}
