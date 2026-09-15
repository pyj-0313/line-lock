package com.linelock.linelock.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// User 엔티티의 DB 접근 창구. JpaRepository 상속만으로 save/findById/findAll 등 기본 CRUD 확보
public interface UserRepository extends JpaRepository<User, Long> {

    // 메서드 이름(LoginId)이 User의 loginId 필드와 일치해야 Spring이 쿼리를 자동 생성함
    Optional<User> findByLoginId(String loginId);

}
