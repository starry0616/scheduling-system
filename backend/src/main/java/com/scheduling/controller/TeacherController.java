package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.TeacherRequest;
import com.scheduling.dto.TeacherResponse;
import com.scheduling.dto.UserCandidateResponse;
import com.scheduling.service.TeacherService;
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
 * 教师基础管理接口 (第3步第一阶段)
 *
 * GET    /teachers       - 教师列表(支持 keyword 模糊查询)
 * GET    /teachers/{id}  - 教师详情
 * POST   /teachers       - 新增教师
 * PUT    /teachers/{id}  - 修改教师
 * DELETE /teachers/{id}  - 删除教师
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN
 */
@RestController
@RequestMapping("/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping
    public Result<List<TeacherResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.success(teacherService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<TeacherResponse> detail(@PathVariable Long id) {
        return Result.success(teacherService.getById(id));
    }

    /**
     * 可绑定教师的候选登录账号(8.3-S 第二批 C1 前端接入):
     * role=TEACHER、status=1、未被其它教师绑定; currentTeacherId 可选(编辑场景并入当前绑定账号);
     * 仅管理员可见(避免泄露其它角色账号)。
     */
    @GetMapping("/user-candidates")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<List<UserCandidateResponse>> userCandidates(
            @RequestParam(required = false) Long currentTeacherId) {
        return Result.success(teacherService.findUserCandidates(currentTeacherId));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<TeacherResponse> create(@Valid @RequestBody TeacherRequest request) {
        return Result.success("新增成功", teacherService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<TeacherResponse> update(@PathVariable Long id, @Valid @RequestBody TeacherRequest request) {
        return Result.success("修改成功", teacherService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        teacherService.delete(id);
        return Result.success("删除成功", null);
    }
}
