// 서버 여러 대(멀티 인스턴스) 환경에서 같은 설비로 동시에 예약 요청을 쏘는 k6 스크립트.
// 한 k6 프로세스가 가상유저를 서버들에 번갈아 배정해서, 서로 다른 서버로 가는 요청이 거의 같은 순간에 출발하게 함
// (k6를 서버마다 따로 실행하면 시작 시점이 어긋나서 경쟁 상태가 재현되지 않을 수 있음)
//
// JWT 서명키가 서버 시작 시마다 랜덤 생성되므로 서버별로 로그인해서 받은 토큰을 PORTS와 같은 순서로 넘겨야 함
//
// 실행 예시:
//   PORTS="8081,8082" TOKENS="토큰1,토큰2" MODE=memory-lock EQUIPMENT_ID=1 VUS=20 k6 run backend/scripts/k6/reserve-multi-instance.js

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const MODE = __ENV.MODE || 'memory-lock';
const EQUIPMENT_ID = __ENV.EQUIPMENT_ID || 1;
const VUS = __ENV.VUS ? parseInt(__ENV.VUS) : 20;
const PORTS = (__ENV.PORTS || '8081,8082').split(',');
const TOKENS = (__ENV.TOKENS || '').split(',');

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
  const idx = (exec.vu.idInTest - 1) % PORTS.length;
  const url = `http://localhost:${PORTS[idx]}/api/demo/reserve/${MODE}/${EQUIPMENT_ID}`;
  const payload = JSON.stringify({
    requester: { id: 3 },
    description: `k6 멀티인스턴스 (${MODE})`,
    startTime: '2026-09-29T10:00:00',
    endTime: '2026-09-29T12:00:00',
  });
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${TOKENS[idx]}`,
    },
  };

  const res = http.post(url, payload, params);
  check(res, {
    '성공(200)': (r) => r.status === 200,
  });
}
