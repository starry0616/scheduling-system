package com.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间段实体 - 对应 time_slot 表
 *
 * 系统固定 5天×5大节 = 25 个时间段。
 * 时间段身份由 (dayOfWeek, period) 复合唯一确定, 对应数据库唯一键 uk_day_period。
 *
 * ⚠ 重要约定(后续模拟退火算法依赖):
 *   时间连续性判断必须基于 (dayOfWeek, period), 禁止使用 slotId+1 之类相邻 id 推断。
 *   例如 durationSlots=2 连续占用需满足: 同一 dayOfWeek 且 nextPeriod = period + 1 的时间段存在。
 */
@Entity
@Table(name = "time_slot", uniqueConstraints = @UniqueConstraint(
        name = "uk_day_period",
        columnNames = {"day_of_week", "period"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 星期几: 1=周一 ... 5=周五 */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    /** 大节序号: 1=第1-2节, 2=第3-4节, 3=第5-6节, 4=第7-8节, 5=第9-10节 */
    @Column(nullable = false)
    private Integer period;

    /** 开始时间, 如 08:00 */
    @Column(name = "start_time", length = 10)
    private String startTime;

    /** 结束时间, 如 09:35 */
    @Column(name = "end_time", length = 10)
    private String endTime;
}
