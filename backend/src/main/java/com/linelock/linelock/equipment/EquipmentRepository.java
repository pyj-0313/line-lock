package com.linelock.linelock.equipment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

// Equipment 엔티티의 DB 접근 창구. JpaRepository 상속만으로 save/findById/findAll 등 기본 CRUD 확보
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Equipment> findWithLockById(Long id);
    
}
