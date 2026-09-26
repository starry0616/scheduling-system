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
 * 班级实体 - 对应 class 表
 *
 * 说明: "class" 为 SQL/Java 常用保留字, 实体类命名 Clazz 规避关键字冲突,
 *       表名映射仍为 class。
 */
@Entity
@Table(name = "class")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Clazz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 班级名称(唯一), 如: 计科2301 */
    @Column(name = "class_name", nullable = false, unique = true, length = 50)
    private String className;

    /** 年级, 如: 2023级 */
    @Column(length = 10)
    private String grade;

    /** 班级人数 */
    @Column(name = "student_count", nullable = false)
    private Integer studentCount;

    /** 所属院系 */
    @Column(length = 100)
    private String department;
}
