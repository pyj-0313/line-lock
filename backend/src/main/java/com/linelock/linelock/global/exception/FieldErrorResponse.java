package com.linelock.linelock.global.exception;

// 입력값 검증 실패 시 "어느 필드가 왜 틀렸는지" 하나를 나타내는 응답 조각. ErrorResponse의 fieldErrors 목록에 담김
// 프론트가 field(예: loginId)를 보고 해당 입력창 옆에 message("아이디를 입력해주세요.")를 표시할 수 있게 하려는 것
// 한 요청에서 여러 필드가 동시에 틀릴 수 있어서 항상 목록으로 내려감
public record FieldErrorResponse(String field, String message) {

}
