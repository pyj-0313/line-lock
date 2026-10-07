package com.linelock.linelock.workorder;

import java.time.LocalDateTime;

// 설비 예약 요청 전용 객체(DTO). 클라이언트가 정해도 되는 값(내용, 시간)만 받는다.
// requester(요청자)와 status(상태)는 일부러 뺐다: 요청자는 로그인한 사용자(토큰)에서, 상태는 서버 로직에서 정함
// -> 본문에 다른 사람 id나 CONFIRMED를 넣어 보내도 받을 곳이 없어서 조작이 불가능함. 설비는 URL 경로(/{equipmentId}/reserve)로 받음
public record ReserveRequest(String description, LocalDateTime startTime, LocalDateTime endTime) {

}
