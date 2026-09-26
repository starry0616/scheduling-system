package com.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 排课任务可用教室(覆盖式保存)请求 (第3步第三阶段 3C-3)
 *
 * 语义: classroomIds 为本次希望纳入的全部教室ID集合, 为空表示清空;
 *       元素去重、null 元素校验、教室存在性校验由服务层完成。
 */
@Data
public class SchedulingTaskClassroomRequest {

    /** 目标教室ID集合(允许为空=清空) */
    @NotNull(message = "教室列表不能为空")
    private List<Long> classroomIds;
}
