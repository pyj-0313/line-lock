package com.linelock.linelock.workorder;

import org.springframework.data.jpa.repository.JpaRepository;

// WorkOrder 엔티티의 DB 접근 창구. JpaRepository 상속만으로 save/findById/findAll 등 기본 CRUD 확보
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

}
