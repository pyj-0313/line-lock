package com.linelock.linelock.workorder;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 설비 예약 요청 전용 객체(DTO). 클라이언트가 정해도 되는 값(내용, 시간)만 받는다.
// requester(요청자)와 status(상태)는 일부러 뺐다: 요청자는 로그인한 사용자(토큰)에서, 상태는 서버 로직에서 정함
// -> 본문에 다른 사람 id나 CONFIRMED를 넣어 보내도 받을 곳이 없어서 조작이 불가능함. 설비는 URL 경로(/{equipmentId}/reserve)로 받음
// 입력값 규칙은 컨트롤러 파라미터의 @Valid가 있어야 실제로 검사됨 (규칙만 선언하고 @Valid를 빼먹으면 아무 검사도 안 일어남)
public record ReserveRequest(

        // 255자 제한: DB 컬럼 길이(기본 255)를 넘는 입력이 DB 에러(500)로 터지기 전에 미리 400으로 막으려는 것
        @NotBlank(message = "작업 내용을 입력해주세요.") @Size(max = 255, message = "작업 내용은 255자 이하여야 합니다.") String description,

        @NotNull(message = "시작 시간을 입력해주세요.") LocalDateTime startTime,

        @NotNull(message = "종료 시간을 입력해주세요.") LocalDateTime endTime) {

    // 필드 하나만으로는 검사할 수 없는 "두 필드의 관계" 규칙. 필드 위 어노테이션은 자기 필드만 볼 수 있어서,
    // 모든 필드를 볼 수 있는 메서드에 @AssertTrue를 붙임 (true를 돌려줘야 통과, false면 검증 실패)
    // 메서드 이름이 isXxx인 이유: Bean Validation이 is로 시작하는 boolean 메서드를 프로퍼티로 보고 검사하며,
    // 오류의 field 이름이 endAfterStart로 나옴 (프론트가 어느 칸에 표시할지 알 수 있게 이름을 정함)
    // @JsonIgnore: 검사용 메서드가 JSON에 endAfterStart 필드로 끼어들지 않게 함
    @JsonIgnore
    @AssertTrue(message = "종료 시간은 시작 시간보다 뒤여야 합니다.")
    public boolean isEndAfterStart() {
        // 둘 중 하나라도 null이면 비교할 수 없으므로 true: 그 경우는 위 @NotNull이 "시간을 입력해주세요"로 따로 잡음
        // 여기서도 false를 주면 같은 원인으로 오류가 두 번 나와 혼란스러움 (규칙마다 자기 책임만 지게 함)
        if (startTime == null || endTime == null) {
            return true;
        }
        // 같은 시각(길이 0의 예약)도 거부: isAfter는 "엄격히 뒤"일 때만 true
        return endTime.isAfter(startTime);
    }
}
