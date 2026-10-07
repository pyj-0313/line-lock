package com.linelock.linelock.user;

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

    // GET /api/users/{id} : id로 사용자 하나 조회
    // User 엔티티가 아니라 UserResponse(DTO)로 응답 -> password 해시가 응답에 섞여 나가지 않음
    // 변환은 서비스가 아니라 컨트롤러에서 함: "밖으로 내보낼 모양"을 정하는 건 API 입구의 몫
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) {
        return UserResponse.from(userService.findById(id));
    }
}
