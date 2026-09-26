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
 * 排课任务-开课实例关联实体 - 对应 scheduling_task_course 表 (第3步第三阶段 3C-3)
 *
 * 领域语义: 表达"本次排课任务纳入哪些开课实例", 构成任务排课范围快照。
 *   后续排课执行阶段据此读取每个 CourseOffering 的 teacher/班级集合/weeklySessions,
 *   展开 SchedulingUnit 并生成排课; 同一 offering 可被多个任务引用(本阶段不建占用锁)。
 *
 * 约束语义:
 *   - (task_id, course_offering_id) 唯一, 防止重复纳入(与 schema.sql uk_task_offering 一致);
 *   - schema.sql 约定: 删除任务时由数据库 ON DELETE CASCADE 清理本表;
 *     因本库表由 JPA ddl-auto=update 自动维护(不含 DB 外键),
 *     该级联语义由 SchedulingTaskService.delete 批量删除本表行保证;
 *   - offering 侧不级联: 被任务引用的开课实例不可删除
 *     (由 CourseOfferingService.delete 的 existsByCourseOfferingId 保护转为友好异常)。
 *
 * 外键列按本项目既有模式以 Long 引用映射, 不声明 @ManyToOne。
 */
@Entity
@Table(name = "scheduling_task_course",
        uniqueConstraints = @UniqueConstraint(name = "uk_task_offering",
                columnNames = {"task_id", "course_offering_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulingTaskCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 排课任务ID(scheduling_task.id) */
    @Column(name = "task_id", nullable = false)
    private Long schedulingTaskId;

    /** 开课实例ID(course_offering.id) */
    @Column(name = "course_offering_id", nullable = false)
    private Long courseOfferingId;
}
