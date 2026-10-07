package com.linelock.linelock.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 로그인 요청 DTO. 규칙 어노테이션은 "이 필드는 이런 규칙"이라는 선언일 뿐이고, 실제로 검사를 실행시키는 건
// 컨트롤러 파라미터의 @Valid임 (@Valid를 빼먹으면 규칙이 있어도 빈 값이 그대로 통과함 - 직접 확인함)
// 로그인에는 길이/형식 규칙을 일부러 안 붙임: 규칙이 바뀌기 전에 가입한 사용자가 옛 비밀번호로 로그인할 수 있어야 하고,
// "비밀번호는 8자 이상" 같은 규칙을 로그인에 걸면 틀린 비밀번호의 형식 정보를 공격자에게 알려주게 됨
@Getter
@AllArgsConstructor
public class LoginRequest {

    // @NotBlank: null, 빈 문자열(""), 공백만 있는 문자열("   ")을 모두 거부 (문자열 전용). 비어 있으면 DB 조회 전에 바로 거절
    // message: 검증 실패 시 응답의 fieldErrors에 그대로 담겨 사용자 화면에 보일 문구라서 "무엇을 해달라"는 안내 형태로 씀
    @NotBlank(message = "아이디를 입력해주세요.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}
