package com.linelock.linelock.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 회원가입 요청 DTO. 규칙은 컨트롤러 파라미터의 @Valid가 있어야 실제로 검사됨 (role 필드가 없는 것은 Part 5와 같은 이유로 의도된 것)
// 가입에는 비밀번호 길이 규칙을 걸지만 로그인(LoginRequest)에는 안 거는 이유: 가입은 비밀번호를 "새로 정하는" 시점이라
// 지금 규칙을 요구할 수 있고, 로그인은 이미 정해진 값을 해시와 비교하는 시점이라 규칙이 나중에 강화돼도 옛 사용자가 로그인할 수 있어야 함
@Getter
@AllArgsConstructor
public class SignupRequest {

    @NotBlank(message = "아이디를 입력해주세요.")
    private String loginId;

    // @NotBlank와 @Size를 함께 씀: 빈 값("")이면 두 규칙을 모두 어겨 같은 필드(password)의 오류가 둘 다 나올 수 있음
    // (min = 8은 일반적으로 많이 쓰는 최소 길이. 복잡도(특수문자 등) 정책은 이번 범위 밖)
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "이름을 입력해주세요.")
    private String name;
}
