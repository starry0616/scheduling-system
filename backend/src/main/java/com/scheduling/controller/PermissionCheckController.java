package com.scheduling.controller;

import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Constants;
import com.scheduling.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限验证示例接口 (第二阶段)
 *
 * GET /api/admin/permission-check - 仅 ADMIN 角色可访问
 * 用于验证"角色拦截机制"(测试 8/9/10: TEACHER/STUDENT 访问应返回 403)。
 * 第三阶段实现业务接口后, 本类可作为参考或删除。
 */
@RestController
@RequestMapping("/admin")
public class PermissionCheckController {

    @GetMapping("/permission-check")
    @RequireRole(Constants.ROLE_ADMIN)
    public Result<String> permissionCheck() {
        return Result.success("权限验证通过, 当前角色: ADMIN");
    }
}
