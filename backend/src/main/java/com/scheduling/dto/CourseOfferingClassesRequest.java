package com.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 开课实例关联班级(覆盖式保存)请求
 *
 * 语义: classIds 为本次希望关联的全部班级ID集合, 为空表示清空全部关联;
 *       元素去重、null 元素校验、班级存在性校验由服务层完成。
 */
@Data
public class CourseOfferingClassesRequest {

    /** 目标班级ID集合(允许为空=清空) */
    @NotNull(message = "班级列表不能为空")
    private List<Long> classIds;
}
