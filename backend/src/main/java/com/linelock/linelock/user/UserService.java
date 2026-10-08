package com.linelock.linelock.user;

import org.springframework.stereotype.Service;

import com.linelock.linelock.global.exception.CustomException;
import com.linelock.linelock.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(userRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성 주입
public class UserService {

    private final UserRepository userRepository; // UserRepository를 주입받음

    public User findById(Long id) { // User 엔티티를 id로 조회하는 메서드
        return userRepository.findById(id).orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)); // 없으면 CustomException(USER_NOT_FOUND) -> 핸들러가 404로 응답
    }

    // 로그인 아이디(토큰에서 꺼낸 값)로 User 조회. /api/users/me처럼 "지금 요청한 본인"의 정보가 필요할 때 씀
    // 없으면 USER_NOT_FOUND(404)가 아니라 UNAUTHORIZED(401): 토큰은 유효한데 그 사용자가 DB에 없는 경우(토큰 발급 후 계정 삭제 등)라서
    // 문제의 본질이 "찾는 자원이 없다"가 아니라 "요청한 사람의 신원을 인정할 수 없다"이기 때문 (findById는 반대로 남의 id를 찾는 것이라 404가 맞음)
    public User findByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
    }

    public User save(User user) { // User 엔티티를 저장하는 메서드
        return userRepository.save(user); // JpaRepository의 save 메서드를 사용하여 User 엔티티를 저장
    }
}
