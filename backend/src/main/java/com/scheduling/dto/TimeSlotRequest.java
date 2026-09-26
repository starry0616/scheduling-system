package com.scheduling.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 时间段新增/修改请求
 *
 * 系统固定 5天×5大节:
 *   dayOfWeek ∈ [1,5] (1=周一), period ∈ [1,5] (大节序号)
 * 越界值在参数校验阶段即被拒绝(HTTP 400)。
 */
@Data
public class TimeSlotRequest {

    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期非法, 必须是1-5(1=周一)")
    @Max(value = 5, message = "星期非法, 必须是1-5(1=周一)")
    private Integer dayOfWeek;

    @NotNull(message = "节次不能为空")
    @Min(value = 1, message = "节次非法, 必须是1-5(大节序号)")
    @Max(value = 5, message = "节次非法, 必须是1-5(大节序号)")
    private Integer period;

    @Size(max = 10, message = "开始时间长度不能超过10")
    private String startTime;

    @Size(max = 10, message = "结束时间长度不能超过10")
    private String endTime;
}
