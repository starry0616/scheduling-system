package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.CourseOfferingRequest;
import com.scheduling.dto.CourseOfferingResponse;
import com.scheduling.service.CourseOfferingService;
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
 * 开课实例管理接口 (第3步第三阶段 3C-1)
 *
 * GET    /course-offerings          - 列表(支持 semester 精确过滤)
 * GET    /course-offerings/{id}     - 详情
 * POST   /course-offerings          - 新增
 * PUT    /course-offerings/{id}     - 修改
 * DELETE /course-offerings/{id}     - 删除
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN
 */
@RestController
@RequestMapping("/course-offerings")
@RequiredArgsConstructor
public class CourseOfferingController {

    private final CourseOfferingService courseOfferingService;

    @GetMapping
    public Result<List<CourseOfferingResponse>> list(@RequestParam(required = false) String semester) {
        return Result.success(courseOfferingService.list(semester));
    }

    @GetMapping("/{id}")
    public Result<CourseOfferingResponse> detail(@PathVariable Long id) {
        return Result.success(courseOfferingService.getById(id));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<CourseOfferingResponse> create(@Valid @RequestBody CourseOfferingRequest request) {
        return Result.success("新增成功", courseOfferingService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<CourseOfferingResponse> update(@PathVariable Long id, @Valid @RequestBody CourseOfferingRequest request) {
        return Result.success("修改成功", courseOfferingService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        courseOfferingService.delete(id);
        return Result.success("删除成功", null);
    }
}
