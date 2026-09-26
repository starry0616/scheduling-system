package com.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息 DTO
 * 登录(/auth/login)与获取当前用户(/auth/me)共用，作为嵌套 user 对象返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoResponse {

    /** 用户ID */
    private Long id;

    private String username;

    private String realName;

    private String role;

    private String phone;

    private Integer status;
}
