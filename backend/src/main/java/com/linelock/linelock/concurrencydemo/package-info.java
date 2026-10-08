/**
 * 동시성 제어 방식 비교 실험 전용 코드 (락 없음 / 메모리락 / 비관적 / 낙관적 / Redis, 점검 알림 배치 중복 실행 방지).
 *
 * <p>이 패키지의 모든 컨트롤러와 서비스는 {@code demo} 프로필에서만 빈으로 등록된다
 * ({@code --spring.profiles.active=demo}). 기본 실행(운영)에서는 {@code /api/demo/**} 주소가 아예 존재하지 않는다(404).
 *
 * <p>이유: 이 코드는 비교 실험을 위해 요청 본문으로 엔티티({@code WorkOrder})를 그대로 받는 옛 방식을 일부러 유지한다.
 * 그래서 {@code requester}, {@code status}를 클라이언트가 마음대로 정할 수 있고(다른 사람 이름으로 예약 가능),
 * 동시성 버그를 일부러 재현하는 코드도 있어서 운영에 열려 있으면 안 된다. 정식 예약 API는
 * {@code /api/workorders/{equipmentId}/reserve}(요청자는 토큰에서 결정, Redis 분산락)이다.
 *
 * <p>주의: 이 패키지의 클래스는 서로 의존하므로 모두 같은 프로필이어야 한다. 일부만 빠지면 의존하는 빈이 없어 서버 기동이 실패한다.
 * 이 규칙은 {@code ConcurrencyDemoProfileTest}가 지킨다.
 */
package com.linelock.linelock.concurrencydemo;
