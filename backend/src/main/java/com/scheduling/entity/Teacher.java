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
 * 教师实体 - 对应 teacher 表
 *
 * user_id 关联 sys_user(id), 一个登录账号对应一位教师(唯一)。
 */
@Entity
@Table(name = "teacher")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联用户ID(sys_user.id), 唯一 */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 教师工号(唯一) */
    @Column(name = "teacher_no", nullable = false, unique = true, length = 20)
    private String teacherNo;

    /** 教师姓名 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 职称: 教授/副教授/讲师 */
    @Column(length = 50)
    private String title;

    /** 所属院系 */
    @Column(length = 100)
    private String department;
}
