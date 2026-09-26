package com.scheduling.config;

import com.scheduling.util.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器
 * 拦截请求，验证 JWT Token，将用户信息存入 request attribute
 * 白名单路径 (登录/注册等) 不需要 Token
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements Filter {

    private final JwtUtil jwtUtil;

    /** 不需要认证的路径 */
    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login"
    );

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 白名单路径放行
        if (isWhiteListed(path)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 提取 Token
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token缺失\",\"data\":null}");
            return;
        }

        String token = header.substring(7);

        // 验证 Token
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\",\"data\":null}");
            return;
        }

        // Token 有效，将用户信息存入 request attribute 供后续使用
        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);
        String role = jwtUtil.getRole(token);

        request.setAttribute("userId", userId);
        request.setAttribute("username", username);
        request.setAttribute("role", role);

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::equals);
    }
}
