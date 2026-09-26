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
 * 教师时间偏好实体 - 对应 teacher_preference 表 (V2.2 §9 S1)
 *
 * 领域语义: 表达"教师在某时间段是否偏好上课"(软约束 S1 数据源)。
 *   - preference_level: 1=偏好, -1=不偏好, 0=一般(与 schema.sql 注释一致);
 *   - S1 惩罚规则(算法层 FitnessCalculator): 每占用一个教师"不偏好(-1)"的时间段 +1;
 *   - (teacher_id, time_slot_id) 唯一(与 schema.sql uk_teacher_slot 一致);
 *   - 无记录视为 0(一般), 不构成任何偏好惩罚。
 *
 * 本表是纯数据源: 排课执行时由 ExecutionService 读取并转换为
 * Map&lt;Long(timeSlotId), Integer(preferenceLevel)&gt; 传入算法层,
 * JPA 实体从不进入算法核心。
 */
@Entity
@Table(name = "teacher_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_teacher_slot",
                columnNames = {"teacher_id", "time_slot_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 教师ID(teacher.id) */
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** 时间段ID(time_slot.id) */
    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    /** 偏好级别: 1=偏好, -1=不偏好, 0=一般 */
    @Builder.Default
    @Column(name = "preference_level", nullable = false)
    private Integer preferenceLevel = 0;
}
