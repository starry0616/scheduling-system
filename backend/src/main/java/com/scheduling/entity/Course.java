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
 * 课程实体 - 对应 course 表
 *
 * 课程是"可开课的基础元数据"(不绑定教师/班级/学期)。
 * 一次具体的开课(某学期、某教师、某班级)由 CourseOffering 表达, 见后续阶段。
 */
@Entity
@Table(name = "course")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 课程代码(唯一) */
    @Column(name = "course_code", nullable = false, unique = true, length = 20)
    private String courseCode;

    /** 课程名称 */
    @Column(name = "course_name", nullable = false, length = 100)
    private String courseName;

    /** 课程类型: THEORY/LAB, 与数据库默认值保持一致 */
    @Builder.Default
    @Column(name = "course_type", length = 20)
    private String courseType = "THEORY";

    /** 要求教室类型: NORMAL/MULTIMEDIA/LAB, 与数据库默认值保持一致 */
    @Builder.Default
    @Column(name = "required_room_type", length = 20)
    private String requiredRoomType = "NORMAL";
}
