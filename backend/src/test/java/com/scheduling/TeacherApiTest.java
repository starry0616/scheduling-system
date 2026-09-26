package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教师基础管理 Web 层测试
 * 覆盖: 正常请求/参数校验/查询/新增/修改/删除/不存在数据异常/user_id 外键与唯一约束
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeacherApiTest extends BaseCrudApiTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 新建一个可被教师绑定的测试用户(唯一), 返回 userId */
    private long createTempUser() {
        User user = userRepository.save(User.builder()
                .username("t" + System.nanoTime())
                .password(ENCODER.encode("test123"))
                .realName("测试教师")
                .role(Constants.ROLE_TEACHER)
                .status(1)
                .build());
        return user.getId();
    }

    private void deleteTempUser(long userId) {
        try {
            userRepository.deleteById(userId);
        } catch (RuntimeException e) {
            // 教师记录可能未清理成功(FK引用), 忽略清理失败避免掩盖主断言
        }
    }

    private Map<String, Object> body(long userId, String teacherNo, String name, String title, String department) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("teacherNo", teacherNo);
        m.put("name", name);
        m.put("title", title);
        m.put("department", department);
        return m;
    }

    @Test
    @DisplayName("教师1: 新增成功后按id查询详情, 并清理临时用户")
    void createAndDetail() throws Exception {
        long userId = createTempUser();
        String no = "T" + uniqueSuffix();
        try {
            long id = createAndGetId("/api/teachers", body(userId, no, "王立", "副教授", "计算机学院"), adminToken());

            getJson("/api/teachers/" + id, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.teacherNo").value(no))
                    .andExpect(jsonPath("$.data.name").value("王立"))
                    .andExpect(jsonPath("$.data.userId").value(userId));

            deleteJson("/api/teachers/" + id, adminToken()).andExpect(status().isOk());
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师2: 参数校验失败 -> HTTP400, 关联用户/工号/姓名为空")
    void createValidationFails() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", null);
        m.put("teacherNo", "  ");
        m.put("name", null);
        postJson("/api/teachers", m, adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("关联用户ID不能为空")))
                .andExpect(jsonPath("$.message").value(containsString("教师工号不能为空")));
    }

    @Test
    @DisplayName("教师3: 关联用户不存在 -> code404")
    void createUserNotFound() throws Exception {
        postJson("/api/teachers", body(99999999L, "T" + uniqueSuffix(), "无名", null, null), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("关联用户不存在")));
    }

    @Test
    @DisplayName("教师4: 重复工号 -> code400")
    void createDuplicateTeacherNo() throws Exception {
        long userA = createTempUser();
        long userB = createTempUser();
        String no = "DUP" + uniqueSuffix();
        try {
            long idA = createAndGetId("/api/teachers", body(userA, no, "李四", null, null), adminToken());
            postJson("/api/teachers", body(userB, no, "赵五", null, null), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("教师工号已存在")));
            deleteJson("/api/teachers/" + idA, adminToken()).andExpect(status().isOk());
        } finally {
            deleteTempUser(userA);
            deleteTempUser(userB);
        }
    }

    @Test
    @DisplayName("教师5: 同一用户重复绑定 -> code400")
    void createSameUserBoundTwice() throws Exception {
        long userId = createTempUser();
        String no1 = "B1" + uniqueSuffix();
        try {
            long idA = createAndGetId("/api/teachers", body(userId, no1, "孙老师", null, null), adminToken());
            postJson("/api/teachers", body(userId, "B2" + uniqueSuffix(), "另一个孙老师", null, null), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("已绑定")));
            deleteJson("/api/teachers/" + idA, adminToken()).andExpect(status().isOk());
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师6: 列表 + 关键字模糊查询")
    void listWithKeyword() throws Exception {
        long userId = createTempUser();
        String no = "T" + uniqueSuffix();
        String name = "查得到" + uniqueSuffix();
        try {
            long id = createAndGetId("/api/teachers", body(userId, no, name, "教授", "信息学院"), adminToken());

            getJson("/api/teachers", Map.of("keyword", no), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].teacherNo").value(no));

            getJson("/api/teachers", Map.of("keyword", name), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(id));

            deleteJson("/api/teachers/" + id, adminToken()).andExpect(status().isOk());
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师7: 修改成功")
    void updateSuccess() throws Exception {
        long userId = createTempUser();
        String no = "UP" + uniqueSuffix();
        try {
            long id = createAndGetId("/api/teachers", body(userId, no, "周平", "讲师", "数学学院"), adminToken());

            putJson("/api/teachers/" + id, body(userId, no, "周平", "副教授", "计算机学院"), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("副教授"))
                    .andExpect(jsonPath("$.data.department").value("计算机学院"));

            deleteJson("/api/teachers/" + id, adminToken()).andExpect(status().isOk());
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师8: 修改不存在的教师 -> code404")
    void updateNotFound() throws Exception {
        long userId = createTempUser();
        try {
            putJson("/api/teachers/99999999", body(userId, "X1", "不存在", null, null), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("教师不存在")));
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师9: 删除成功后查询 -> code404")
    void deleteSuccessThenNotFound() throws Exception {
        long userId = createTempUser();
        String no = "DEL" + uniqueSuffix();
        try {
            long id = createAndGetId("/api/teachers", body(userId, no, "待删除教师", null, null), adminToken());

            deleteJson("/api/teachers/" + id, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            getJson("/api/teachers/" + id, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师10: 查询不存在的教师 -> code404")
    void detailNotFound() throws Exception {
        getJson("/api/teachers/99999999", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("教师不存在")));
    }

    @Test
    @DisplayName("教师11: 候选账号仅包含未绑定教师的 TEACHER 账号(8.3-S C1)")
    void userCandidatesOnlyUnboundTeacherAccounts() throws Exception {
        long userId = createTempUser();
        try {
            // 新建(未绑定)的 TEACHER 账号必须出现在候选中
            getJson("/api/teachers/user-candidates", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[?(@.id == " + userId + ")]").exists());
        } finally {
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("教师12: 已被教师绑定的账号不再出现在候选; 非 ADMIN 访问候选 -> 403")
    void userCandidatesExcludeBoundAndRoleGuard() throws Exception {
        long userId = createTempUser();
        String no = "CB" + uniqueSuffix();
        try {
            long id = createAndGetId("/api/teachers", body(userId, no, "候选排除", null, null), adminToken());

            getJson("/api/teachers/user-candidates", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[?(@.id == " + userId + ")]").doesNotExist());

            // 非 ADMIN(TEACHER 登录)访问候选账号列表必须被 RBAC 拒绝(避免账号泄露)
            getJson("/api/teachers/user-candidates", teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            deleteJson("/api/teachers/" + id, adminToken()).andExpect(status().isOk());
        } finally {
            deleteTempUser(userId);
        }
    }
}
