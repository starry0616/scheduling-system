package com.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开课实例新增/修改请求
 *
 * course_id/teacher_id 对应资源必须存在(由服务层校验);
 * weeklySessions 一周需要安排几次课; durationSlots 每次课连续占用几个大节,
 * 两者语义独立、不可合并。durationSlots 缺省为 1(理论课), 与 course_offering 表 DEFAULT 一致。
 * isLabCourse 为派生字段(durationSlots>1), 不通过本请求传入。
 */
@Data
public class CourseOfferingRequest {

    @NotNull(message = "课程不能为空")
    private Long courseId;

    @NotNull(message = "教师不能为空")
    private Long teacherId;

    @NotBlank(message = "学期不能为空")
    @Size(max = 20, message = "学期长度不能超过20")
    private String semester;

    @NotNull(message = "每周上课次数不能为空")
    @Min(value = 1, message = "每周上课次数必须为正整数")
    private Integer weeklySessions;

    /** 每次课连续占用大节数, 理论课=1, 实验课=2; 缺省按 1 处理 */
    @Min(value = 1, message = "每次课连续占用大节数必须为正整数")
    private Integer durationSlots = 1;
}
