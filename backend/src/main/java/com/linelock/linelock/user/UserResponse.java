package com.linelock.linelock.user;

// 사용자 조회 API의 응답 전용 객체(DTO). User 엔티티를 그대로 응답하면 password(암호화된 해시)와 role이 JSON에
// 같이 나가므로(직접 확인함), 응답에 내보내도 되는 필드만 골라 담는다. password/role은 일부러 뺐고,
// 필요해지면(관리자 화면 등) 그때 추가한다. record라서 생성자/getter(id(), loginId(), name())가 자동 생성되고 불변이다
public record UserResponse(Long id, String loginId, String name) {

    // User 엔티티 -> 응답 DTO 변환. 새 객체를 "만드는" 메서드라 객체가 아직 없는 시점에 호출해야 해서 static
    // 엔티티에 password getter가 있어도 여기서 꺼내지 않으면 응답에 들어갈 방법이 없음 (노출을 코드 구조로 차단)
    // new 안의 값 순서는 위 record 선언의 필드 순서와 같아야 함
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getLoginId(), user.getName());
    }
}
