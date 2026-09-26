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
 * 排课条目-班级关联实体 - 对应 schedule_entry_class 表 (V2.2 §19)
 *
 * 领域语义: 一条排课结果(ScheduleEntry)覆盖的全部授课班级。
 *   - 合班 offering 的每个 Entry 会生成多行(每班一行), 与本条排课的班级集合一致;
 *   - (schedule_entry_id, class_id) 唯一(与 schema.sql uk_entry_class 一致);
 *   - 由 SchedulingExecutionStore 与 ScheduleEntry 在同一保存事务内写入, 无孤儿。
 */
@Entity
@Table(name = "schedule_entry_class",
        uniqueConstraints = @UniqueConstraint(name = "uk_entry_class",
                columnNames = {"schedule_entry_id", "class_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEntryClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 排课条目ID(schedule_entry.id) */
    @Column(name = "schedule_entry_id", nullable = false)
    private Long scheduleEntryId;

    /** 班级ID(class.id) */
    @Column(name = "class_id", nullable = false)
    private Long classId;
}
