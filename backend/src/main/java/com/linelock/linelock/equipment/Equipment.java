package com.linelock.linelock.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity // 이 클래스를 equipment 테이블과 매핑되는 JPA 엔티티로 등록
@Getter // 모든 필드의 getter 메서드를 자동 생성 (Lombok)
@Setter // 추가 - reserve 로직에서 equipment.setStatus() 호출을 위해 필요
public class Equipment {

    @Id // 기본키(PK) 필드
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 값을 직접 넣지 않고 DB가 auto-increment로 채워줌
    private Long id;

    @Column(unique = true, nullable = false) // 설비 고유 번호 - 중복 금지, 빈 값 금지
    private String equipmentNumber;

    @Enumerated(EnumType.STRING) // enum을 숫자(0,1,2)가 아닌 문자열("RUNNING" 등)로 DB에 저장
    private EquipmentStatus status;

    @Version // 낙관적 락용 필드. 수정될 때마다 값이 자동 증가하며, 동시 수정 충돌을 감지하는 데 쓰임
    private Long version;
}
