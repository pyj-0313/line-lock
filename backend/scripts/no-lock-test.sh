#!/bin/bash

# 동시성 로드맵 1단계(락 없음) 버그 재현용 스크립트.
# 같은 설비(id=1)에 5개의 예약 요청을 실제로 "동시에" 쏴서, 락 없이는 이중예약이 재현되는지 확인한다.
# 실행 전: 서버 실행 + 설비 id=1을 IDLE로 리셋 + 아래 TOKEN을 로그인해서 받은 값으로 교체할 것
TOKEN="로그인해서_받은_토큰으로_교체"

for i in 1 2 3 4 5
do
  # 끝의 & : 이 curl을 백그라운드로 던지고 바로 다음 반복으로 넘어감 -> 5개가 거의 동시에 시작됨
  # (& 없이 순차 실행하면 앞 요청이 완전히 끝난 뒤 다음 요청이 시작되어 동시성 버그 자체가 재현 안 됨)
  curl -s -X POST http://localhost:8080/api/workorders/1/reserve \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"requester\":{\"id\":2},\"description\":\"동시성 테스트 $i\",\"startTime\":\"2026-09-21T10:00:00\",\"endTime\":\"2026-09-21T12:00:00\"}" \
    -o /tmp/result_$i.json \
    -w "요청 $i -> HTTP 상태코드: %{http_code}\n" &
    # -o : 응답 본문을 화면 대신 파일로 저장 (5개 프로세스가 동시에 화면에 쓰면 출력이 겹쳐서 신뢰 불가)
    # -w : 응답의 HTTP 상태 코드만 뽑아서 출력
done

wait   # 백그라운드로 던진 5개가 전부 끝날 때까지 대기 (없으면 아래 결과 출력이 요청 완료 전에 먼저 실행됨)
echo "=== 결과 파일 내용 ==="
for i in 1 2 3 4 5
do
  echo "--- result_$i.json ---"
  cat /tmp/result_$i.json
  echo ""
done