package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.CourseRequest;
import com.scheduling.dto.CourseResponse;
import com.scheduling.service.CourseService;
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
 * 课程基础管理接口 (第3步第一阶段)
 *
 * GET    /courses       - 课程列表(支持 keyword 模糊查询)
 * GET    /courses/{id}  - 课程详情
 * POST   /courses       - 新增课程
 * PUT    /courses/{id}  - 修改课程
 * DELETE /courses/{id}  - 删除课程
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    public Result<List<CourseResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.success(courseService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<CourseResponse> detail(@PathVariable Long id) {
        return Result.success(courseService.getById(id));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        return Result.success("新增成功", courseService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<CourseResponse> update(@PathVariable Long id, @Valid @RequestBody CourseRequest request) {
        return Result.success("修改成功", courseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return Result.success("删除成功", null);
    }
}
