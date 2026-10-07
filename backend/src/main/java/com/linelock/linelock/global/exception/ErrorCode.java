package com.linelock.linelock.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 이 서버가 클라이언트에게 알려주는 "실패 종류" 전체 목록. 항목마다 HTTP 상태코드와 사용자용 메시지를 한 곳에 모아둠
// (메시지 문구를 바꾸고 싶으면 코드 곳곳이 아니라 여기만 고치면 됨. 새 실패 종류가 생기면 한 줄만 추가)
// 응답 본문의 code 값은 이 상수의 이름(예: EQUIPMENT_NOT_FOUND)이라, 프론트가 메시지 문구가 아니라 이 이름으로 분기할 수 있음
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 이미 있는 값과 충돌하는 요청이라 400(요청 형식 오류)이 아니라 409
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),
    // "없는 아이디"와 "틀린 비밀번호"를 일부러 구분하지 않고 하나로 둠 -> 응답만 보고 가입된 아이디를 알아내는 계정 열거 공격 방지
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    EQUIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 설비입니다."),
    WORK_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 작업지시입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    EQUIPMENT_IN_USE(HttpStatus.CONFLICT, "이미 사용 중인 설비입니다."),
    // 락을 못 얻었다는 건 "같은 설비를 다른 요청이 지금 처리 중"이라는 뜻이라, 서버 장애(503)가 아니라 자원 충돌(409)로 봄
    LOCK_ACQUISITION_FAILED(HttpStatus.CONFLICT, "다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."),
    // 낙관적 락(@Version) 충돌: 같은 행을 동시에 수정하다 먼저 저장한 쪽에 밀린 경우. 다시 시도하면 대부분 풀리는 일시적 충돌
    CONCURRENT_UPDATE_CONFLICT(HttpStatus.CONFLICT, "동시에 수정되어 충돌했습니다. 다시 시도해주세요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    // 클라이언트가 잘못 보낸 요청 전반: 깨진 JSON, 타입 불일치(/equipments/abc), 입력값 검증 실패(@Valid)
    // 검증 실패일 때는 응답에 fieldErrors(필드별 사유)가 함께 내려가고, 그 외에는 이 공통 메시지만 나감
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    // 토큰은 유효한데 해당 사용자가 DB에 없는 경우처럼 "요청자의 신원을 인정할 수 없다"는 상황, 404가 아니라 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다."),
    // 예상 못한 오류의 응답용. 실제 원인(스택트레이스)은 응답이 아니라 서버 로그에만 남기고, 밖에는 이 메시지만 내보냄
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
