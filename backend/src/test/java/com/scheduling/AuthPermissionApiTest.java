package com.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduling.common.Constants;
import com.scheduling.entity.User;
import com.scheduling.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层角色拦截 MockMvc 测试
 *
 * 覆盖完整请求链路: JwtAuthenticationFilter(认证) -> RoleInterceptor(授权) -> Controller
 * 测试目标接口: GET /api/admin/permission-check (仅 ADMIN)
 *
 * 场景:
 * 1. 无 Token  -> HTTP 401 (Filter)
 * 2. 无效 Token -> HTTP 401 (Filter)
 * 3. TEACHER 访问 -> HTTP 403 (Interceptor, 角色不匹配)
 * 4. ADMIN 访问   -> HTTP 200 (通过)
 *
 * 说明: 应用配置了 context-path=/api, @AutoConfigureMockMvc 会将该 context-path
 * 应用到 MockMvc 请求, 因此所有请求 URL 需写完整路径(如 /api/auth/login),
 * 与真实请求链保持一致。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthPermissionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeAll
    static void ensureUsers(@Autowired UserRepository userRepository) {
        // 保证测试账号存在(与 AuthServiceTest 使用同一测试库, 幂等)
        ensureUser(userRepository, "admin", "admin123", "管理员", Constants.ROLE_ADMIN);
        ensureUser(userRepository, "teacher01", "teacher123", "张老师", Constants.ROLE_TEACHER);
    }

    private static void ensureUser(UserRepository repo, String username, String password, String realName, String role) {
        if (repo.findByUsername(username).isEmpty()) {
            repo.save(User.builder()
                    .username(username)
                    .password(encoder.encode(password))
                    .realName(realName)
                    .role(role)
                    .status(1)
                    .build());
        }
    }

    /** 通过真实 /auth/login 接口登录, 返回 Token */
    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, result.getResponse().getStatus(), "登录接口失败, 响应体: " + body);
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("token").asText();
    }

    @Test
    @DisplayName("角色权限1: 无Token访问ADMIN接口 -> HTTP 401")
    void noTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/permission-check").contextPath("/api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("角色权限2: 无效Token访问ADMIN接口 -> HTTP 401")
    void invalidTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/permission-check").contextPath("/api")
                        .header("Authorization", "Bearer invalid.token.string"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("角色权限3: TEACHER访问ADMIN接口 -> HTTP 403")
    void teacherAccessAdminApiShouldReturn403() throws Exception {
        String token = login("teacher01", "teacher123");
        assertFalse(token.isEmpty(), "TEACHER 登录应返回 Token");

        mockMvc.perform(get("/api/admin/permission-check").contextPath("/api")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("角色权限4: ADMIN访问ADMIN接口 -> HTTP 200")
    void adminAccessAdminApiShouldReturn200() throws Exception {
        String token = login("admin", "admin123");
        assertFalse(token.isEmpty(), "ADMIN 登录应返回 Token");

        mockMvc.perform(get("/api/admin/permission-check").contextPath("/api")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
