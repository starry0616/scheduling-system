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
 * 开课实例-班级关联实体 - 对应 course_offering_class 表 (第3步第三阶段 3C-2)
 *
 * 领域语义: CourseOffering 面向的授课对象(一个开课实例可对应多个班级, 即合班上课)。
 *   CourseOffering = 某学期某课程某教师的"开课"
 *   CourseOfferingClass = 该开课覆盖的班级集合
 *   后续 SchedulingUnit 按 classIds 汇总班级人数(totalStudents 求和),
 *   同一 Offering 的所有班级共享同一时间/教室(合班排课)。
 *
 * 约束语义:
 *   - (course_offering_id, class_id) 唯一, 防止同一班级重复关联(与 schema.sql uk_offering_class 一致);
 *   - schema.sql 约定: 删除开课实例时由数据库 ON DELETE CASCADE 清理关联行;
 *     因本库表由 JPA ddl-auto=update 自动维护(不含 ON DELETE CASCADE),
 *     该级联语义在 CourseOfferingService.delete 中由服务层显式先行删除关联行, 与脚本意图保持一致;
 *   - class 侧不级联: 被关联的班级不可删除(由 ClazzService 的 DataIntegrityViolation 转为友好异常)。
 *
 * 外键列按本项目既有模式以 Long 引用映射, 不声明 @ManyToOne。
 */
@Entity
@Table(name = "course_offering_class",
        uniqueConstraints = @UniqueConstraint(name = "uk_offering_class",
                columnNames = {"course_offering_id", "class_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseOfferingClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 开课实例ID(course_offering.id) */
    @Column(name = "course_offering_id", nullable = false)
    private Long courseOfferingId;

    /** 班级ID(class.id) */
    @Column(name = "class_id", nullable = false)
    private Long classId;
}
