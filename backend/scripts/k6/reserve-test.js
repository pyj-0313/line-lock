// 동시성 로드맵 4단계(락없음/비관적/낙관적/Redis) 비교용 k6 부하테스트 스크립트.
// 실행 전: 서버를 demo 프로필로 실행 + 설비를 IDLE로 리셋 + 아래 환경변수를 채워서 실행할 것
//   서버 실행: java -jar build/libs/linelock-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
//   (이 스크립트가 호출하는 /api/demo/** 는 demo 프로필에서만 등록됨. 기본 실행에서는 404)
//
// 실행 예시 (설비 1번, 방식은 no-lock, 동시 유저 10명):
//   TOKEN="로그인해서 받은 토큰" MODE=no-lock EQUIPMENT_ID=1 VUS=10 k6 run backend/scripts/k6/reserve-test.js
//
// MODE는 no-lock / memory-lock / pessimistic / optimistic / redis 중 하나
// PORT는 요청을 보낼 서버 포트(기본 8080). 서버 2대 실험에선 PORT만 바꿔 k6를 2개 동시에 실행

import http from 'k6/http';
import { check } from 'k6';

const MODE = __ENV.MODE || 'no-lock';
const TOKEN = __ENV.TOKEN;
const EQUIPMENT_ID = __ENV.EQUIPMENT_ID || 1;
const VUS = __ENV.VUS ? parseInt(__ENV.VUS) : 10;
const PORT = __ENV.PORT || 8080;

export const options = {
  scenarios: {
    reserve_burst: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '10s',
    },
  },
};

export default function () {
  const url = `http://localhost:${PORT}/api/demo/reserve/${MODE}/${EQUIPMENT_ID}`;
  const payload = JSON.stringify({
    requester: { id: 3 },
    description: `k6 부하테스트 (${MODE})`,
    startTime: '2026-09-29T10:00:00',
    endTime: '2026-09-29T12:00:00',
  });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${TOKEN}`,
    },
  };

  const res = http.post(url, payload, params);
  check(res, {
    '성공(200)': (r) => r.status === 200,
  });
}
