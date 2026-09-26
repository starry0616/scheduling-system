package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.ClassroomResponse;
import com.scheduling.dto.CourseOfferingOptionResponse;
import com.scheduling.dto.CourseOfferingResponse;
import com.scheduling.dto.SchedulingTaskClassroomRequest;
import com.scheduling.dto.SchedulingTaskOfferingRequest;
import com.scheduling.dto.SchedulingTaskRequest;
import com.scheduling.dto.SchedulingRunResponse;
import com.scheduling.dto.SchedulingTaskResponse;
import com.scheduling.execution.SchedulingExecutionService;
import com.scheduling.service.SchedulingTaskService;
import com.scheduling.vo.SchedulingTaskData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排课任务管理接口 (第3步第三阶段 3C-3)
 *
 * GET    /scheduling-tasks                     - 任务列表(支持 semester/status 可选过滤)
 * GET    /scheduling-tasks/{id}                - 任务详情
 * POST   /scheduling-tasks                     - 新增任务(状态固定 PENDING, hardConstraintWeight=null)
 * PUT    /scheduling-tasks/{id}                - 修改任务(仅 PENDING; 禁止改 status/hardConstraintWeight)
 * DELETE /scheduling-tasks/{id}                - 删除任务(仅 PENDING; 级联清理两张范围关联表)
 * GET/PUT /scheduling-tasks/{id}/course-offerings - 查询/覆盖式保存任务纳入的开课实例
 * GET/PUT /scheduling-tasks/{id}/classrooms       - 查询/覆盖式保存任务可用教室池
 * POST    /scheduling-tasks/{id}/run              - 执行一次排课(ADMIN; 仅 PENDING 允许)
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN(与既有基础管理模块保持一致)。
 * run 之后 result/fitness/进度等读取接口在后续阶段补充。
 */
@RestController
@RequestMapping("/scheduling-tasks")
@RequiredArgsConstructor
public class SchedulingTaskController {

    private final SchedulingTaskService schedulingTaskService;
    private final SchedulingExecutionService schedulingExecutionService;

    @GetMapping
    public Result<List<SchedulingTaskResponse>> list(@RequestParam(required = false) String semester,
                                                     @RequestParam(required = false) String status) {
        return Result.success(schedulingTaskService.list(semester, status));
    }

    @GetMapping("/{id}")
    public Result<SchedulingTaskResponse> detail(@PathVariable Long id) {
        return Result.success(schedulingTaskService.getById(id));
    }

    /**
     * 任务完整数据(阶段七前端接入): 任务本体 + 纳入的开课实例(含班级上下文) + 教室池 + 全量时间段。
     * 供任务详情页一次性展示当前课程/教室范围。
     */
    @GetMapping("/{id}/data")
    public Result<SchedulingTaskData> fullData(@PathVariable Long id) {
        return Result.success(schedulingTaskService.getSchedulingTaskData(id));
    }

    /** 任务课程配置候选(阶段七): 与任务同学期的全部开课实例(含班级), 供"添加课程"弹窗使用 */
    @GetMapping("/{id}/offering-options")
    public Result<List<CourseOfferingOptionResponse>> offeringOptions(@PathVariable Long id) {
        return Result.success(schedulingTaskService.listOfferingOptions(id));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<SchedulingTaskResponse> create(@Valid @RequestBody SchedulingTaskRequest request) {
        return Result.success("新增成功", schedulingTaskService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<SchedulingTaskResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody SchedulingTaskRequest request) {
        return Result.success("修改成功", schedulingTaskService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        schedulingTaskService.delete(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/{taskId}/course-offerings")
    public Result<List<CourseOfferingResponse>> listCourseOfferings(@PathVariable Long taskId) {
        return Result.success(schedulingTaskService.listCourseOfferings(taskId));
    }

    @PutMapping("/{taskId}/course-offerings")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<List<CourseOfferingResponse>> replaceCourseOfferings(
            @PathVariable Long taskId,
            @Valid @RequestBody SchedulingTaskOfferingRequest request) {
        return Result.success("保存成功", schedulingTaskService.replaceCourseOfferings(taskId, request));
    }

    @GetMapping("/{taskId}/classrooms")
    public Result<List<ClassroomResponse>> listClassrooms(@PathVariable Long taskId) {
        return Result.success(schedulingTaskService.listClassrooms(taskId));
    }

    @PutMapping("/{taskId}/classrooms")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<List<ClassroomResponse>> replaceClassrooms(
            @PathVariable Long taskId,
            @Valid @RequestBody SchedulingTaskClassroomRequest request) {
        return Result.success("保存成功", schedulingTaskService.replaceClassrooms(taskId, request));
    }

    /**
     * 执行一次排课(阶段六)。
     * 同步执行: 成功返回 FEASIBLE/BEST_EFFORT 摘要; 数据不可行/系统异常时任务置 FAILED,
     * 并向调用方返回携带原因的 400/500 业务错误(由全局异常处理器输出)。
     */
    @PostMapping("/{taskId}/run")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<SchedulingRunResponse> run(@PathVariable Long taskId) {
        return Result.success("排课执行完成", schedulingExecutionService.run(taskId));
    }
}
