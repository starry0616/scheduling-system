package com.scheduling.repository;

import com.scheduling.entity.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 时间段 Repository
 */
@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Optional<TimeSlot> findByDayOfWeekAndPeriod(Integer dayOfWeek, Integer period);

    boolean existsByDayOfWeekAndPeriod(Integer dayOfWeek, Integer period);

    /**
     * 按 (dayOfWeek, period) 升序排列的全量时间段。
     * 后续算法/前端课表均依赖此稳定顺序: 周一第1节 → 周一第2节 → ... → 周五第5节。
     */
    List<TimeSlot> findAllByOrderByDayOfWeekAscPeriodAsc();
}
