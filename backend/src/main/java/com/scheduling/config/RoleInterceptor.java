package com.scheduling.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduling.annotation.RequireRole;
import com.scheduling.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色授权拦截器
 *
 * 职责边界:
 * - 身份认证(是否已登录 / Token 是否有效)由 JwtAuthenticationFilter 完成 -> 未通过返回 401
 * - 本拦截器只做"角色授权"检查: 目标方法标注 @RequireRole 时,
 *   从当前请求 request attribute 读取角色(JwtAuthenticationFilter 已写入), 不重新查询数据库
 *   -> 已登录但角色不匹配返回 403
 */
@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    /** JwtAuthenticationFilter 写入的当前用户角色 attribute 名 */
    public static final String ATTR_ROLE = "role";

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非控制器方法(静态资源/预检请求等)直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法上优先取 @RequireRole, 其次取类上 @RequireRole
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        // 未标注 @RequireRole -> 所有已登录角色均可访问
        if (requireRole == null) {
            return true;
        }

        // 从 request attribute 读取当前角色(JwtAuthenticationFilter 认证阶段写入)
        String role = (String) request.getAttribute(ATTR_ROLE);
        if (role == null) {
            // 理论上不会出现: 非白名单路径在 JwtAuthenticationFilter 已强制要求有效 Token
            writeJson(response, 401, Result.error(401, "未登录或Token已失效"));
            return false;
        }

        boolean allowed = Arrays.asList(requireRole.value()).contains(role);
        if (!allowed) {
            writeJson(response, 403, Result.error(403,
                    "无权限访问, 该接口需要角色: " + String.join("/", requireRole.value())));
            return false;
        }

        return true;
    }

    private void writeJson(HttpServletResponse response, int status, Result<?> body) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
