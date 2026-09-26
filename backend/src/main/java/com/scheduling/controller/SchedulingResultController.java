package com.scheduling.controller;

import com.scheduling.common.Result;
import com.scheduling.dto.ScheduleEntryView;
import com.scheduling.dto.SchedulingRunResponse;
import com.scheduling.service.SchedulingResultQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排课结果只读查询接口(阶段七前端接入)
 *
 * GET /scheduling-results/task/{taskId}        - 按任务查询结果统计摘要(任务未执行返回 404)
 * GET /scheduling-results/{resultId}/schedule  - 按结果ID查询完整课表条目(含课程/教师/教室/班级)
 *
 * 仅提供读取, 查询接口登录即可; 不触碰执行/状态迁移。
 */
@RestController
@RequestMapping("/scheduling-results")
@RequiredArgsConstructor
public class SchedulingResultController {

    private final SchedulingResultQueryService resultQueryService;

    @GetMapping("/task/{taskId}")
    public Result<SchedulingRunResponse> summaryByTask(@PathVariable Long taskId) {
        return Result.success(resultQueryService.getSummaryByTask(taskId));
    }

    @GetMapping("/{resultId}/schedule")
    public Result<List<ScheduleEntryView>> schedule(@PathVariable Long resultId) {
        return Result.success(resultQueryService.getSchedule(resultId));
    }
}
