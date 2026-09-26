package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import com.scheduling.dto.ClazzResponse;
import com.scheduling.dto.CourseOfferingClassesRequest;
import com.scheduling.service.CourseOfferingClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开课实例-班级关联接口 (第3步第三阶段 3C-2)
 *
 * GET /course-offerings/{offeringId}/classes  - 查询该开课实例已关联的班级
 * PUT /course-offerings/{offeringId}/classes  - 覆盖式保存关联班级(classIds, 空=清空)
 *
 * 权限: 查询接口登录即可, 写接口仅 ADMIN(与 /course-offerings 主资源保持一致)
 */
@RestController
@RequestMapping("/course-offerings/{offeringId}/classes")
@RequiredArgsConstructor
public class CourseOfferingClassController {

    private final CourseOfferingClassService courseOfferingClassService;

    @GetMapping
    public Result<List<ClazzResponse>> list(@PathVariable Long offeringId) {
        return Result.success(courseOfferingClassService.listClasses(offeringId));
    }

    @PutMapping
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<List<ClazzResponse>> replace(@PathVariable Long offeringId,
                                               @Valid @RequestBody CourseOfferingClassesRequest request) {
        return Result.success("保存成功", courseOfferingClassService.replaceClasses(offeringId, request));
    }
}
