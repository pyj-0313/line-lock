package com.linelock.linelock.global.exception;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 실패 시 클라이언트가 받는 JSON 응답의 모양: {"status":404,"code":"EQUIPMENT_NOT_FOUND","message":"..."}
// 입력값 검증 실패(400)일 때만 틀린 필드 목록이 추가로 붙음: {..., "fieldErrors":[{"field":"loginId","message":"..."}]}
// @Getter가 있어야 Jackson이 필드를 읽어서 JSON으로 바꿈 (없으면 응답 본문이 비어 나감)
// @JsonInclude(NON_NULL): 값이 null인 필드는 JSON에서 아예 뺌 -> fieldErrors가 필요 없는 일반 에러 응답에는 이 키가 안 나타남
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status; // HTTP 상태코드 숫자 (본문에도 넣어두면 프론트가 상태줄을 따로 안 봐도 됨)
    private String code; // ErrorCode 상수 이름. 프론트가 분기할 때 쓰는 값
    private String message; // 사용자에게 보여줄 안내 문구
    private List<FieldErrorResponse> fieldErrors; // 검증 실패 때만 채워짐 (어느 필드가 왜 틀렸는지). 그 외에는 null

    // ErrorCode 하나로 응답 객체를 만드는 팩토리 메서드. 객체가 아직 없는 상태에서 호출하므로 static
    // new ErrorResponse(404, "...", "...")를 매번 직접 쓰는 것보다 값이 어긋나는 실수가 적음
    // 필드 목록이 필요 없는 일반 에러용: fieldErrors에 null을 넣어 JSON에서 빠지게 함
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage(),
                null);

    }

    // 입력값 검증 실패용: 위와 같은 응답에 "틀린 필드와 사유 목록"을 추가로 담음 (이름이 같고 인자만 다른 오버로딩)
    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorResponse> fieldErrors) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage(),
                fieldErrors);
    }

}
