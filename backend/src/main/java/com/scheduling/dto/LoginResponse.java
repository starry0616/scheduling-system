package com.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO - 嵌套 token + user 结构
 * {
 *   "token": "...",
 *   "user": { "id":1, "username":"admin", "realName":"系统管理员", "role":"ADMIN" }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** 当前登录用户信息 */
    private UserInfoResponse user;
}
