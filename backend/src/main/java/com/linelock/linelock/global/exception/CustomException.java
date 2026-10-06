package com.linelock.linelock.global.exception;

import lombok.Getter;

// 서비스 계층에서 "이런 이유로 실패했다"를 알릴 때 던지는 예외. 실패 종류마다 예외 클래스를 따로 만들지 않고,
// 안에 담긴 ErrorCode 값으로 구분함 (GlobalExceptionHandler가 이 값을 꺼내 상태코드와 메시지를 정함)
// RuntimeException을 상속한 unchecked 예외라서 호출하는 메서드마다 throws나 try/catch를 쓰지 않아도 됨
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        // 부모에게 메시지를 넘겨두면 로그에 예외가 찍힐 때 의미 있는 메시지가 같이 남음
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
