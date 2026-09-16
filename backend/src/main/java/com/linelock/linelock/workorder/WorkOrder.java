package com.linelock.linelock.workorder;

import java.time.LocalDateTime;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter 
public class WorkOrder {

    @Id // 기본키(PK) 필드
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 값을 직접 넣지 않고 DB가 auto-increment로 채워줌
    private Long id;

    @ManyToOne // 설비 - WorkOrder와 Equipment는 다대일(N:1) 관계
    private Equipment equipment;

    @ManyToOne // 요청자 - WorkOrder와 User는 다대일(N:1) 관계
    private User requester;

    @Column(columnDefinition = "TEXT") // 작업 내용 - 길이 제한 없이 저장
    private String description;

    @Column(name = "start_time", nullable = false) // 설비 사용 시작 시간
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false) // 설비 사용 종료 시간
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING) // enum을 문자열로 저장 (PENDING, CONFIRMED 등)
    private WorkOrderStatus status;
}
