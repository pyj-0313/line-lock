package com.linelock.linelock.concurrencydemo;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

// 점검 알림 배치 중복 실행 방지 데모 전용 컨트롤러. 예약 데모(ConcurrencyDemoController)와 성격이 달라 별도로 분리함.
// 요청 본문이 필요 없고, "조회"가 아니라 작업을 실행시키는 요청이라 POST를 사용
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/demo/batch")
public class MaintenanceNoticeDemoController {

    private final MaintenanceNoticeDemoService maintenanceNoticeDemoService;

    // 락 없음: 서버 2대에 동시에 호출하면 둘 다 "실행됨"
    @PostMapping("/no-lock")
    public String runWithoutLock() {
        return maintenanceNoticeDemoService.runWithoutLock();
    }

    // Redis 락: 서버 2대에 동시에 호출하면 한 대만 "실행됨", 다른 한 대는 "건너뜀"
    @PostMapping("/redis-lock")
    public String runWithRedisLock() {
        return maintenanceNoticeDemoService.runWithRedisLock();
    }
    
}
