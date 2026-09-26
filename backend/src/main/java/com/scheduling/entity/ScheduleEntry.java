package com.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排课结果条目实体 - 对应 schedule_entry 表 (V2.2 §19)
 *
 * 领域语义: 一个 SchedulingUnit(= 一次实际排课课次) 对应一行 ScheduleEntry:
 *   - weeklySessions = k 的 offering 会展开为 k 个 Unit, 因此产生 k 行 Entry;
 *   - durationSlots = d 的课次连续占用同一天 d 个大节, 但只存"起始 time_slot_id"
 *     (schema.sql 无 duration_slots/结束时间段列, 连续语义由起始时间段 + offering.duration_slots 恢复);
 *   - unit_index 记录该课次在其 offering 内的课次序号(0-based sessionIndex)。
 *
 * 由 SchedulingExecutionStore 在保存事务内写入; 与 task 之间 1:N。
 */
@Entity
@Table(name = "schedule_entry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 排课任务ID(scheduling_task.id) */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 开课实例ID(course_offering.id) */
    @Column(name = "course_offering_id", nullable = false)
    private Long courseOfferingId;

    /** 教师ID(teacher.id) */
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** 教室ID(classroom.id) */
    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    /** 起始时间段ID(time_slot.id, 该课次连续占用的第一个大节) */
    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    /** 课次序号(该课次在其 offering 内的 sessionIndex, 0-based) */
    @Column(name = "unit_index", nullable = false)
    private Integer unitIndex;
}
