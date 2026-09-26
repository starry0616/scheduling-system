package com.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 排课任务纳入开课实例(覆盖式保存)请求 (第3步第三阶段 3C-3)
 *
 * 语义: courseOfferingIds 为本次希望纳入的全部开课实例ID集合, 为空表示清空;
 *       元素去重、null 元素校验、开课实例存在性、学期一致性校验由服务层完成。
 */
@Data
public class SchedulingTaskOfferingRequest {

    /** 目标开课实例ID集合(允许为空=清空) */
    @NotNull(message = "开课实例列表不能为空")
    private List<Long> courseOfferingIds;
}
