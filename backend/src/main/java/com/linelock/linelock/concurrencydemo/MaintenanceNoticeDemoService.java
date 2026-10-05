package com.linelock.linelock.concurrencydemo;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// "서버가 몇 대든 점검 알림 배치는 딱 한 번만 실행돼야 한다"는 상황을 흉내 낸 데모.
// 설비 예약과 달리 보호할 대상이 DB 테이블의 행이 아니라 "이 작업을 누가 실행하느냐"라서,
// 잠글 행이 없어 DB 락(비관적/낙관적)을 쓸 수 없고 Redis 분산락만 적용할 수 있다는 걸 보여주는 용도.
// (실제 서비스라면 @Scheduled로 자동 실행하겠지만, 두 서버에 같은 순간 요청을 보내려고 엔드포인트로 대신 호출함)
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceNoticeDemoService {

    private final RedissonClient redissonClient;

    // 어느 서버가 실행했는지 로그로 구분하려고 이 서버의 포트를 읽어옴 (--server.port로 덮어쓴 값도 반영됨)
    @Value("${server.port}")
    private String port;

    // 락 없이 실행: 서버 2대에 동시에 요청하면 둘 다 실행되어 알림이 중복 발송되는 문제를 재현하는 버전
    public String runWithoutLock() {
        log.info("[포트 {}] 점검 알림 배치 실행 시작", port);
        try{
        Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[포트 {}] 점검 알림 배치 실행 끝", port);
        return "실행됨";
    }

    // Redis 락으로 감싸서 실행: 서버가 몇 대든 동시에 시도하면 락을 잡은 한 대만 실행하고 나머지는 건너뜀
    // 한계: 작업이 끝나면 락을 풀기 때문에, 다른 서버가 끝난 "뒤에" 요청하면 또 실행됨
    // (동시 실행만 막을 뿐 "이미 한 번 실행했는지"는 기억하지 않음 - 직접 확인함)
    public String runWithRedisLock() {
        // DB 행이 아니라 "이름"만으로 락을 건다는 게 핵심. 모든 서버가 같은 이름을 써야 같은 락으로 인식됨
        RLock lock = redissonClient.getLock("maintenance-notice-batch");
        boolean isLocked = false;
        try {
            // waitTime=0: 예약 때(5초 대기)와 달리 "이미 누가 하고 있으면 기다리지 않고 바로 포기"해야 해서 0
            // leaseTime=10초: 락을 쥔 서버가 도중에 죽어도 10초 뒤 자동으로 풀려서 영구 잠김을 막는 안전장치
            isLocked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                // 예외를 던지지 않는 이유: 배치는 다른 서버가 대신 실행 중이면 나는 안 해도 정상이라 에러가 아님
                log.info("[포트 {}] 가 다른 서버가 이미 실행 중이라 건너뜀", port);
                return "건너뜀";
            }
            doBatchWork();
            return "실행됨";
        } catch (InterruptedException e) {
            // 락 대기 중 스레드가 멈추라는 신호를 받은 경우. 신호를 다시 표시해두고 중단 상태로 응답
            Thread.currentThread().interrupt();
            return "중단됨";
        } finally {
            // 락을 실제로 얻은 경우에만 풀어야 함 (못 얻었는데 unlock하면 오류)
            if (isLocked) {
                lock.unlock();
            }

        }
    }

    // 실제 배치 작업을 흉내 내는 부분. 2초를 일부러 걸리게 해서, 그동안 다른 서버가 락을 시도할 때
    // 이미 잠겨 있는 상태가 되도록 함 (작업이 순식간에 끝나면 겹치는 구간이 없어 락 효과가 안 보임)
    private void doBatchWork() {
        log.info("[포트 {}] 점검 알림 배치 실행 시작", port);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[포트 {}] 점검 알림 배치 실행 끝", port);
    }
}