package com.linelock.linelock.concurrencydemo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.linelock.linelock.equipment.Equipment;
import com.linelock.linelock.equipment.EquipmentRepository;
import com.linelock.linelock.equipment.EquipmentStatus;
import com.linelock.linelock.user.UserRepository;
import com.linelock.linelock.workorder.WorkOrder;
import com.linelock.linelock.workorder.WorkOrderRepository;
import com.linelock.linelock.workorder.WorkOrderStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemoryLockReserveService {

    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final Set<Long> lockedEquipmentIds = ConcurrentHashMap.newKeySet();

    public WorkOrder reserve(Long equipmentId, WorkOrder workOrder) {
        if (!lockedEquipmentIds.add(equipmentId)) {
            throw new IllegalStateException("이미 사용 중인 설비입니다.");
        }

        try {
            Equipment equipment = equipmentRepository.findById(equipmentId).orElseThrow();
            if (equipment.getStatus() == EquipmentStatus.IDLE) {
                equipmentRepository.updateStatusIgnoringVersion(equipmentId, EquipmentStatus.RUNNING);

                workOrder.setEquipment(equipment);
                workOrder.setRequester(userRepository.getReferenceById(workOrder.getRequester().getId()));
                workOrder.setStatus(WorkOrderStatus.CONFIRMED);
                return workOrderRepository.save(workOrder);
            }
            throw new IllegalStateException("이미 사용 중인 설비입니다.");
        } finally {
            lockedEquipmentIds.remove(equipmentId);
        }
    }
}
