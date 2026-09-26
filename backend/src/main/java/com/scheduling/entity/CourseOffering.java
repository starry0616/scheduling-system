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
 * 开课实例实体 - 对应 course_offering 表
 *
 * 表示"某个学期中, 由某位教师承担的一门具体课程教学实例"(第3步第三阶段):
 *   Course        = 课程定义(元数据)
 *   Teacher       = 教师资源
 *   CourseOffering = 课程 + 教师 + 学期 + 排课属性组成的具体教学实例
 *
 * ⚠ 排课属性语义约定(后续 SchedulingUnit/排课算法依赖):
 *   weeklySessions: 一周需要安排几次课(如理论课 2 次);
 *   durationSlots : 每次课连续占用几个大节(理论课=1, 实验课=2)。
 *   不要把 weeklySessions × durationSlots 混成一个字段;
 *   后续算法按 weeklySessions 将本实例拆分为多个具体教学单元。
 *   isLabCourse = durationSlots > 1, 由服务层派生, 不通过请求直接写入。
 *
 * ⚠ 连续性约定(与 TimeSlot 一致):
 *   durationSlots>1 的连续占用由 TimeSlot 的 (dayOfWeek, period) 推导,
 *   禁止使用 slotId + 1 之类的相邻 id 推断连续性。
 *
 * 说明: 外键列(course_id/teacher_id)按本项目既有模式以 Long 引用映射,
 *       不声明 @ManyToOne(与 Teacher.userId 引用 sys_user 的模式一致)。
 */
@Entity
@Table(name = "course_offering")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 课程ID(course.id) */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 教师ID(teacher.id) */
    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    /** 学期, 如: 2026秋 */
    @Column(nullable = false, length = 20)
    private String semester;

    /** 每周上课次数(每次1大节=2学时) */
    @Column(name = "weekly_sessions", nullable = false)
    private Integer weeklySessions;

    /** 每次课连续占用的大节数: 理论课=1, 实验课=2, 与数据库 DEFAULT 1 保持一致 */
    @Builder.Default
    @Column(name = "duration_slots", nullable = false)
    private Integer durationSlots = 1;

    /** 是否实验课: 1是 0否(由服务层按 durationSlots>1 派生), 与数据库 DEFAULT 0 保持一致 */
    @Builder.Default
    @Column(name = "is_lab_course")
    private Integer isLabCourse = 0;
}
