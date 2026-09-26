package com.scheduling.dto;

import com.scheduling.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可绑定教师账号候选 VO (8.3-S 第二批 C1 新增, 只读)
 *
 * <p>教师(teacher)记录必须关联一个已存在且未被其它教师占用的登录账号(user_id),
 * 而系统不提供账号管理接口。为让管理员在前端"新增教师"时能选择合法账号,
 * 提供本只读候选视图: role=TEACHER 且 status=1 且尚未绑定教师的用户。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCandidateResponse {

    private Long id;
    private String username;
    private String realName;

    public static UserCandidateResponse from(User user) {
        return UserCandidateResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .build();
    }
}
