package com.linelock.linelock.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 실패 시 클라이언트가 받는 JSON 응답의 모양: {"status":404,"code":"EQUIPMENT_NOT_FOUND","message":"..."}
// @Getter가 있어야 Jackson이 필드를 읽어서 JSON으로 바꿈 (없으면 응답 본문이 비어 나감)
@Getter
@AllArgsConstructor
public class ErrorResponse {

    private int status;     // HTTP 상태코드 숫자 (본문에도 넣어두면 프론트가 상태줄을 따로 안 봐도 됨)
    private String code;    // ErrorCode 상수 이름. 프론트가 분기할 때 쓰는 값
    private String message; // 사용자에게 보여줄 안내 문구

    // ErrorCode 하나로 응답 객체를 만드는 팩토리 메서드. 객체가 아직 없는 상태에서 호출하므로 static
    // new ErrorResponse(404, "...", "...")를 매번 직접 쓰는 것보다 값이 어긋나는 실수가 적음
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                errorCode.getMessage());
        
    }
    
    
}
