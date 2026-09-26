package com.scheduling.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教师新增/修改请求
 */
@Data
public class TeacherRequest {

    /** 关联登录用户ID(sys_user.id), 唯一 */
    @NotNull(message = "关联用户ID不能为空")
    private Long userId;

    @NotBlank(message = "教师工号不能为空")
    @Size(max = 20, message = "教师工号长度不能超过20")
    private String teacherNo;

    @NotBlank(message = "教师姓名不能为空")
    @Size(max = 50, message = "教师姓名长度不能超过50")
    private String name;

    @Size(max = 50, message = "职称长度不能超过50")
    private String title;

    @Size(max = 100, message = "院系长度不能超过100")
    private String department;
}
