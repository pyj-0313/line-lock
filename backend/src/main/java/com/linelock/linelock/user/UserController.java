package com.linelock.linelock.user;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController // HTTP 요청을 받아 처리하고 반환값을 JSON으로 자동 변환해 응답하는 컨트롤러로 등록
@RequiredArgsConstructor // final 필드(userService)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 의존성 주입
@RequestMapping("/api/users") // 이 클래스의 모든 API 주소는 /api/users로 시작
public class UserController {

    private final UserService userService;

    // GET /api/users/me : 로그인한 본인의 정보 조회 (로그인한 누구나 호출 가능, SecurityConfig에서 /api/users/** ADMIN 규칙보다 먼저 허용함)
    // 프론트가 로그인 직후 이 API로 내 정보와 역할(role)을 받아 관리자 메뉴를 보여줄지 판단하는 용도
    // 조회 대상을 URL이나 본문이 아니라 토큰에서 꺼낸 로그인 아이디(Principal)로 정하므로, 다른 사람의 정보를 조회할 방법이 없음
    // /{id}(ADMIN 전용)와 경로가 겹쳐 보여도, 정확히 일치하는 "/me"가 패턴 "/{id}"보다 우선해서 충돌하지 않음
    @GetMapping("/me")
    public UserResponse getMe(Principal principal) {
        return UserResponse.from(userService.findByLoginId(principal.getName()));
    }

    // GET /api/users/{id} : id로 사용자 하나 조회 (ADMIN만 가능: SecurityConfig의 /api/users/** 규칙, 일반 사용자는 403)
    // User 엔티티가 아니라 UserResponse(DTO)로 응답 -> password 해시가 응답에 섞여 나가지 않음
    // 변환은 서비스가 아니라 컨트롤러에서 함: "밖으로 내보낼 모양"을 정하는 건 API 입구의 몫
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        return UserResponse.from(userService.findById(id));
    }
}
