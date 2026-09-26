package com.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教室新增/修改请求
 */
@Data
public class ClassroomRequest {

    @NotBlank(message = "教室编号不能为空")
    @Size(max = 20, message = "教室编号长度不能超过20")
    private String roomNo;

    @Size(max = 50, message = "楼栋长度不能超过50")
    private String building;

    @NotNull(message = "教室容量不能为空")
    @Min(value = 1, message = "教室容量必须大于等于1")
    private Integer capacity;

    /** NORMAL/MULTIMEDIA/LAB, 缺省 NORMAL, 与数据库默认值保持一致 */
    @Size(max = 20, message = "教室类型长度不能超过20")
    private String roomType = "NORMAL";
}
