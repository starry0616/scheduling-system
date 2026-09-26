package com.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 班级新增/修改请求
 */
@Data
public class ClazzRequest {

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 50, message = "班级名称长度不能超过50")
    private String className;

    @Size(max = 10, message = "年级长度不能超过10")
    private String grade;

    @NotNull(message = "班级人数不能为空")
    @Min(value = 0, message = "班级人数不能小于0")
    private Integer studentCount;

    @Size(max = 100, message = "院系长度不能超过100")
    private String department;
}
