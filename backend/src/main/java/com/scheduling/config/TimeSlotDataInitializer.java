package com.scheduling.config;

import com.scheduling.entity.TimeSlot;
import com.scheduling.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * time_slot 种子数据初始化器 (第3步第二阶段)
 *
 * 系统固定 5天×5大节 = 25 个时间段, 是排课算法核心基础数据。
 * 由于当前环境使用 ddl-auto=update(只建表不插数据), 由本组件在应用启动时:
 *   1) 补齐缺失的 (dayOfWeek, period) 组合;
 *   2) 将已存在但起止时间非标准的记录校准为标准时间(自愈, 时间由系统锁定)。
 * 时间与 schema.sql 注释约定一致:
 *   第1-2节 08:00-09:35 / 第3-4节 10:00-11:35 / 第5-6节 14:00-15:35
 *   第7-8节 16:00-17:35 / 第9-10节 19:00-20:35
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotDataInitializer implements ApplicationRunner {

    private static final int DAY_COUNT = 5;
    private static final int PERIOD_COUNT = 5;

    /** 每个大节的标准起止时间(下标 = period - 1) */
    private static final String[] STANDARD_START_TIMES = {"08:00", "10:00", "14:00", "16:00", "19:00"};
    private static final String[] STANDARD_END_TIMES = {"09:35", "11:35", "15:35", "17:35", "20:35"};

    private final TimeSlotRepository timeSlotRepository;

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        int corrected = 0;
        for (int day = 1; day <= DAY_COUNT; day++) {
            for (int period = 1; period <= PERIOD_COUNT; period++) {
                Optional<TimeSlot> existing = timeSlotRepository.findByDayOfWeekAndPeriod(day, period);
                if (existing.isEmpty()) {
                    timeSlotRepository.save(TimeSlot.builder()
                            .dayOfWeek(day)
                            .period(period)
                            .startTime(STANDARD_START_TIMES[period - 1])
                            .endTime(STANDARD_END_TIMES[period - 1])
                            .build());
                    created++;
                    continue;
                }
                TimeSlot slot = existing.get();
                boolean timeMismatch = !STANDARD_START_TIMES[period - 1].equals(slot.getStartTime())
                        || !STANDARD_END_TIMES[period - 1].equals(slot.getEndTime());
                if (timeMismatch) {
                    slot.setStartTime(STANDARD_START_TIMES[period - 1]);
                    slot.setEndTime(STANDARD_END_TIMES[period - 1]);
                    timeSlotRepository.save(slot);
                    corrected++;
                }
            }
        }
        if (created > 0 || corrected > 0) {
            log.info("time_slot 种子数据自愈完成: 补齐 {} 条, 校准 {} 条(共 {} 条)",
                    created, corrected, DAY_COUNT * PERIOD_COUNT);
        }
    }
}
