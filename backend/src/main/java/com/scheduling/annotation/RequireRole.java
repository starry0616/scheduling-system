package com.scheduling.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解
 *
 * 标注在 Controller 方法(或类)上, 表示仅允许指定角色访问。
 * 配合 {@code RoleInterceptor} 使用:
 * - 身份认证(是否已登录/Token 是否有效)由 JwtAuthenticationFilter 完成 -> 401
 * - 本注解只做角色授权检查, 从 request attribute 读取 role, 不查询数据库 -> 角色不符 403
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 允许访问的角色列表
     * 例: @RequireRole(Constants.ROLE_ADMIN)
     *     @RequireRole({Constants.ROLE_ADMIN, Constants.ROLE_TEACHER})
     */
    String[] value();
}
