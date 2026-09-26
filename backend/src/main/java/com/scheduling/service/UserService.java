package com.scheduling.service;

import com.scheduling.common.Constants;
import com.scheduling.dto.LoginRequest;
import com.scheduling.dto.LoginResponse;
import com.scheduling.dto.UserInfoResponse;
import com.scheduling.entity.User;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.UserRepository;
import com.scheduling.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务 - 处理登录认证逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录
     * 1. 根据用户名查找用户
     * 2. 校验密码 (BCrypt)
     * 3. 校验账号状态 (status=1 启用)
     * 4. 生成 JWT Token 返回
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));

        // 2. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("用户 {} 密码校验失败", request.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 校验账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("用户 {} 账号已禁用", request.getUsername());
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        // 4. 校验角色合法性
        String role = user.getRole();
        if (!isValidRole(role)) {
            log.error("用户 {} 角色非法: {}", request.getUsername(), role);
            throw new BusinessException(403, "账号角色异常，请联系管理员");
        }

        // 5. 生成 Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        log.info("用户 {} 登录成功, 角色: {}", user.getUsername(), user.getRole());

        // 6. 组装响应: token + user(嵌套用户信息)
        return LoginResponse.builder()
                .token(token)
                .user(buildUserInfo(user))
                .build();
    }

    /**
     * 根据 Token 获取当前用户信息
     */
    public UserInfoResponse getUserInfoByToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "Token 无效或已过期");
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            throw new BusinessException(401, "Token 无效");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用");
        }

        return buildUserInfo(user);
    }

    /**
     * 组装用户信息 DTO - 登录与 /auth/me 共用
     */
    private UserInfoResponse buildUserInfo(User user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .phone(user.getPhone())
                .status(user.getStatus())
                .build();
    }

    /**
     * 登出 - JWT 无状态，服务端不需要额外操作
     * 客户端清除 Token 即可
     */
    public void logout(String token) {
        if (token != null) {
            String username = jwtUtil.getUsername(token);
            log.info("用户 {} 登出", username != null ? username : "unknown");
        }
    }

    private boolean isValidRole(String role) {
        return Constants.ROLE_ADMIN.equals(role)
                || Constants.ROLE_TEACHER.equals(role)
                || Constants.ROLE_STUDENT.equals(role);
    }
}
