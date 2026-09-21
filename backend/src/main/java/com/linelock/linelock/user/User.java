package com.linelock.linelock.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity // 이 클래스를 user 테이블과 매핑되는 JPA 엔티티로 등록
@Getter // 모든 필드의 getter 매서드를 자동 생성 (Lombok)
@Setter // 모든 필드의 setter 메서드를 자동 생성 (Lombok) - 회원가입 시 값을 채워 넣기 위해 필요
public class User {

    @Id // 기본키(PK) 필드
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 값을 직접 넣지 않고 DB가 auto-increment로 채워줌
    private Long id;

    @Column(unique = true, nullable = false) // 로그인 아이디 - 중복 금지, 빈 값 금지
    private String loginId;

    @Column(nullable = false) // 비밀번호 - 빈 값 금지
    private String password;

    @Column(nullable = false) // 이름 - 빈 값 금지
    private String name;

    @Enumerated(EnumType.STRING) // enum을 숫자(0,1,2)가 아닌 문자열("ADMIN" 등)로 DB에 저장
    private UserRole role;

}
