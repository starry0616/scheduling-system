package com.scheduling.service;

import com.scheduling.dto.TimeSlotRequest;
import com.scheduling.dto.TimeSlotResponse;
import com.scheduling.entity.TimeSlot;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 时间段服务 (第3步第二阶段)
 *
 * 时间段是排课算法的核心基础数据: 系统固定 5天×5大节=25个, (dayOfWeek, period) 复合唯一。
 * 时间由系统锁定(见 TimeSlotDataInitializer), 本服务只提供只读列表/详情与新增(唯一/范围校验)。
 *
 * ⚠ 连续性约定(后续算法依赖):
 *   禁止使用 slotId+1 推断时间段是否连续, 必须基于 (dayOfWeek, period):
 *   例如 durationSlots=2 需满足同一 dayOfWeek 且 nextPeriod = period + 1, 且该下一时间段必须存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;

    /** 全量时间段, 按 (dayOfWeek, period) 升序 */
    public List<TimeSlotResponse> list() {
        return timeSlotRepository.findAllByOrderByDayOfWeekAscPeriodAsc()
                .stream().map(TimeSlotResponse::from).toList();
    }

    /** 时间段详情 */
    public TimeSlotResponse getById(Long id) {
        return TimeSlotResponse.from(getEntity(id));
    }

    /** 新增时间段(25格满后重复组合将被拒绝) */
    @Transactional
    public TimeSlotResponse create(TimeSlotRequest request) {
        if (timeSlotRepository.existsByDayOfWeekAndPeriod(request.getDayOfWeek(), request.getPeriod())) {
            throw new BusinessException(400, "该时间段已存在: 星期" + request.getDayOfWeek() + " 第" + request.getPeriod() + "大节");
        }
        TimeSlot slot = new TimeSlot();
        apply(slot, request);
        timeSlotRepository.save(slot);
        log.info("新增时间段成功: dayOfWeek={}, period={}", slot.getDayOfWeek(), slot.getPeriod());
        return TimeSlotResponse.from(slot);
    }

    private void apply(TimeSlot slot, TimeSlotRequest request) {
        slot.setDayOfWeek(request.getDayOfWeek());
        slot.setPeriod(request.getPeriod());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
    }

    private TimeSlot getEntity(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "时间段不存在: id=" + id));
    }
}
