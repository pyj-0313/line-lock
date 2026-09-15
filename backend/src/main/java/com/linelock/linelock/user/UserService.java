package com.linelock.linelock.user;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service // 비즈니스 로직을 담당하는 서비스 컴포넌트로 Spring에 등록
@RequiredArgsConstructor // final 필드(userRepository)를 받는 생성자를 자동 생성 (Lombok) -> Spring이 이 생성자로 의존성 주입
public class UserService {

    private final UserRepository userRepository; // UserRepository를 주입받음

    public User findById(Long id) { // User 엔티티를 id로 조회하는 메서드
        return userRepository.findById(id).orElseThrow(); // Optional이라 없으면 예외 발생시킴
    }

    public User save(User user) { // User 엔티티를 저장하는 메서드
        return userRepository.save(user); // JpaRepository의 save 메서드를 사용하여 User 엔티티를 저장
    }
}
