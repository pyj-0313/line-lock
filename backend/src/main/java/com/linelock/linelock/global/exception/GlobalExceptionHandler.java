package com.linelock.linelock.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

// 모든 컨트롤러에서 터진 예외를 한 곳에서 받아 알맞은 상태코드와 JSON으로 바꿔주는 클래스
// 컨트롤러/서비스마다 try/catch를 쓰지 않아도 되고, 에러 응답 형식이 어디서나 똑같아짐
// 핸들러가 여럿이면 Spring은 가장 구체적인 예외 타입의 핸들러를 고름 (Exception은 모든 예외의 부모라 마지막 보루 역할)
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 존재하지 않는 경로를 요청하면 Spring이 던지는 예외. 이 핸들러가 없으면 아래 Exception 핸들러(마지막 보루)가
    // 가로채서 404여야 할 응답이 500으로 나가버림 (실제로 겪음 - 테일즈에서도 같은 문제를 겪었던 유형)
    // 교훈: 마지막 보루 핸들러는 프레임워크가 의도한 4xx 응답까지 삼킬 수 있어서, 그런 예외는 전용 핸들러로 미리 빼줘야 함
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("존재하지 않는 경로: {}", e.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 클라이언트가 잘못 보낸 요청: 숫자 자리에 문자를 넣었거나(MethodArgumentTypeMismatchException, 예: /equipments/abc),
    // 본문 JSON이 깨졌을 때(HttpMessageNotReadableException). 서버 버그가 아니라 요청 쪽 문제라 500이 아니라 400
    // 응답이 같아서 핸들러 하나가 두 예외를 같이 받음. 서로 다른 두 타입을 받으려면 공통 부모인 Exception으로 파라미터를 선언해야 함
    // 클라이언트 잘못은 서버 장애가 아니므로 error가 아니라 warn으로 기록
    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST));

    }

    // 서비스가 의도해서 던진 실패(없는 설비, 중복 아이디 등). 예상된 정상 흐름의 실패라 서버 장애를 뜻하는 error가 아니라 warn으로
    // 기록
    // ResponseEntity를 쓰는 이유: 그냥 객체를 리턴하면 상태코드가 항상 200이 되어버려서, 상태코드를 직접 지정하려고
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("비즈니스 예외: {}", errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    // 낙관적 락(@Version) 충돌은 CustomException이 아니라 Spring/Hibernate가 직접 던지는 예외라 별도 핸들러가
    // 필요
    // 다시 시도하면 대부분 풀리는 일시적 충돌이라 500이 아니라 409로 알려줌. 예상 가능하므로 스택트레이스 없이 메시지만 기록
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("낙관적 락 충돌: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.CONCURRENT_UPDATE_CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.CONCURRENT_UPDATE_CONFLICT));
    }

    // 위 핸들러들이 못 받은 나머지 모든 예외(NullPointerException 같은 진짜 버그)
    // 예외 객체 e를 로그에 같이 넘겨서 스택트레이스는 서버 로그에만 남기고, 응답에는 정리된 메시지만 내보내 내부 정보 노출을 막음
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("예상하지 못한 서버 오류", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
