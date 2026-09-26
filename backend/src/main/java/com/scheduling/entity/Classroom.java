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
 * 教室实体 - 对应 classroom 表
 */
@Entity
@Table(name = "classroom")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 教室编号(唯一), 如: A-101 */
    @Column(name = "room_no", nullable = false, unique = true, length = 20)
    private String roomNo;

    /** 楼栋 */
    @Column(length = 50)
    private String building;

    /** 教室容量 */
    @Column(nullable = false)
    private Integer capacity;

    /** 教室类型: NORMAL/MULTIMEDIA/LAB (数据库 NOT NULL, 与默认值保持一致) */
    @Builder.Default
    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType = "NORMAL";
}
