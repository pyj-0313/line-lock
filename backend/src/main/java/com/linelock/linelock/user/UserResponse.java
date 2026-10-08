package com.linelock.linelock.user;

// 사용자 조회 API의 응답 전용 객체(DTO). User 엔티티를 그대로 응답하면 password(암호화된 해시)가 JSON에
// 같이 나가므로(직접 확인함), 응답에 내보내도 되는 필드만 골라 담는다. password는 일부러 뺐다(해시 노출 방지)
// role은 포함한다: 프론트가 로그인 직후 /api/users/me로 역할을 받아 관리자 메뉴를 보여줄지 판단하는 용도
// (토큰을 프론트가 직접 디코딩해 쓰는 방식 대신, 서버가 알려주는 값을 쓰는 것이 표준)
// role이 나가도 안전한 이유: 이 응답은 본인(/me) 또는 ADMIN만 볼 수 있는 경로에서만 나감 (SecurityConfig의 /api/users 규칙)
// record라서 생성자/getter(id(), loginId(), name(), role())가 자동 생성되고 불변이다
public record UserResponse(Long id, String loginId, String name, UserRole role) {

    // User 엔티티 -> 응답 DTO 변환. 새 객체를 "만드는" 메서드라 객체가 아직 없는 시점에 호출해야 해서 static
    // 엔티티에 password getter가 있어도 여기서 꺼내지 않으면 응답에 들어갈 방법이 없음 (노출을 코드 구조로 차단)
    // new 안의 값 순서는 위 record 선언의 필드 순서와 같아야 함
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLoginId(), user.getName(), user.getRole());
    }
}
