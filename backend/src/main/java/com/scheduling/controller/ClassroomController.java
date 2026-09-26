package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.ClassroomRequest;
import com.scheduling.dto.ClassroomResponse;
import com.scheduling.service.ClassroomService;
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
 * 教室基础管理接口 (第3步第二阶段)
 *
 * GET    /classrooms       - 教室列表(支持 keyword 模糊查询)
 * GET    /classrooms/{id}  - 教室详情
 * POST   /classrooms       - 新增教室
 * PUT    /classrooms/{id}  - 修改教室
 * DELETE /classrooms/{id}  - 删除教室
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN
 */
@RestController
@RequestMapping("/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @GetMapping
    public Result<List<ClassroomResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.success(classroomService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<ClassroomResponse> detail(@PathVariable Long id) {
        return Result.success(classroomService.getById(id));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<ClassroomResponse> create(@Valid @RequestBody ClassroomRequest request) {
        return Result.success("新增成功", classroomService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<ClassroomResponse> update(@PathVariable Long id, @Valid @RequestBody ClassroomRequest request) {
        return Result.success("修改成功", classroomService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        classroomService.delete(id);
        return Result.success("删除成功", null);
    }
}
