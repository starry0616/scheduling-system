package com.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程新增/修改请求
 *
 * courseType / requiredRoomType 缺省时使用字段默认值,
 * 与 course 表 DEFAULT('THEORY'/'NORMAL') 保持一致。
 */
@Data
public class CourseRequest {

    @NotBlank(message = "课程代码不能为空")
    @Size(max = 20, message = "课程代码长度不能超过20")
    private String courseCode;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称长度不能超过100")
    private String courseName;

    /** THEORY/LAB */
    @Size(max = 20, message = "课程类型长度不能超过20")
    private String courseType = "THEORY";

    /** NORMAL/MULTIMEDIA/LAB */
    @Size(max = 20, message = "教室类型长度不能超过20")
    private String requiredRoomType = "NORMAL";
}
