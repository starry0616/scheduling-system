package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.ClazzRequest;
import com.scheduling.dto.ClazzResponse;
import com.scheduling.service.ClazzService;
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
 * 班级基础管理接口 (第3步第一阶段)
 *
 * GET    /classes       - 班级列表(支持 keyword 模糊查询)
 * GET    /classes/{id}  - 班级详情
 * POST   /classes       - 新增班级
 * PUT    /classes/{id}  - 修改班级
 * DELETE /classes/{id}  - 删除班级
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN
 */
@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
public class ClazzController {

    private final ClazzService clazzService;

    @GetMapping
    public Result<List<ClazzResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.success(clazzService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<ClazzResponse> detail(@PathVariable Long id) {
        return Result.success(clazzService.getById(id));
    }

    @PostMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<ClazzResponse> create(@Valid @RequestBody ClazzRequest request) {
        return Result.success("新增成功", clazzService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<ClazzResponse> update(@PathVariable Long id, @Valid @RequestBody ClazzRequest request) {
        return Result.success("修改成功", clazzService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        clazzService.delete(id);
        return Result.success("删除成功", null);
    }
}
