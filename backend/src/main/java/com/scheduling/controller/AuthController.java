package com.scheduling.controller;

import com.scheduling.common.Result;
import com.scheduling.dto.LoginRequest;
import com.scheduling.dto.LoginResponse;
import com.scheduling.dto.UserInfoResponse;
import com.scheduling.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * POST /auth/login  - 用户登录
 * GET  /auth/me     - 获取当前用户信息
 * POST /auth/logout - 登出
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserInfoResponse> me(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return Result.error(401, "未提供认证信息");
        }
        UserInfoResponse userInfo = userService.getUserInfoByToken(token);
        return Result.success(userInfo);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        userService.logout(token);
        return Result.success();
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
