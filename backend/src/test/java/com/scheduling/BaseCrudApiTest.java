package com.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduling.common.Constants;
import com.scheduling.entity.User;
import com.scheduling.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 基础管理 CRUD MockMvc 测试基类
 *
 * 提供: 登录取 Token(admin/teacher01)、JSON 请求封装、唯一后缀生成。
 * 子类沿用既有测试约定: 真实 MySQL 测试库、请求 URL 带 /api 前缀 + contextPath("/api")。
 */
@ActiveProfiles("test")
public abstract class BaseCrudApiTest {

    protected static final String CONTEXT = "/api";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static String adminToken;
    private static String teacherToken;

    @BeforeAll
    static void ensureLoginUsers(@Autowired UserRepository userRepository) {
        // 保证测试账号存在(与既有 Auth 测试同一测试库, 幂等)
        ensureUser(userRepository, "admin", "admin123", "系统管理员", Constants.ROLE_ADMIN);
        ensureUser(userRepository, "teacher01", "teacher123", "张老师", Constants.ROLE_TEACHER);
    }

    protected static void ensureUser(UserRepository repo, String username, String password, String realName, String role) {
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

    /** 唯一后缀, 避免与测试库中遗留数据冲突 */
    protected String uniqueSuffix() {
        return String.valueOf(System.nanoTime());
    }

    /** 通过真实登录接口获取 admin Token(缓存) */
    protected String adminToken() throws Exception {
        if (adminToken == null) {
            adminToken = login("admin", "admin123");
        }
        return adminToken;
    }

    /** 通过真实登录接口获取 teacher Token(缓存) */
    protected String teacherToken() throws Exception {
        if (teacherToken == null) {
            teacherToken = login("teacher01", "teacher123");
        }
        return teacherToken;
    }

    /** 登录并返回 Token */
    protected String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contextPath(CONTEXT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(200, result.getResponse().getStatus(), "登录接口失败, 响应体: " + body);
        JsonNode json = objectMapper.readTree(body);
        return json.path("data").path("token").asText();
    }

    // ---------- HTTP 请求封装 ----------

    protected ResultActions getJson(String uri, String token) throws Exception {
        return mockMvc.perform(build(get(uri).contextPath(CONTEXT), token));
    }

    protected ResultActions getJson(String uri, Map<String, String> params, String token) throws Exception {
        MockHttpServletRequestBuilder req = get(uri).contextPath(CONTEXT);
        if (params != null) {
            params.forEach(req::param);
        }
        return mockMvc.perform(build(req, token));
    }

    protected ResultActions postJson(String uri, Object body, String token) throws Exception {
        return mockMvc.perform(build(post(uri).contextPath(CONTEXT), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions putJson(String uri, Object body, String token) throws Exception {
        return mockMvc.perform(build(put(uri).contextPath(CONTEXT), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions deleteJson(String uri, String token) throws Exception {
        return mockMvc.perform(build(delete(uri).contextPath(CONTEXT), token));
    }

    /** 无 Token 的 POST(用于 401 场景) */
    protected ResultActions postWithoutToken(String uri, Object body) throws Exception {
        return mockMvc.perform(post(uri).contextPath(CONTEXT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private MockHttpServletRequestBuilder build(MockHttpServletRequestBuilder req, String token) {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return req;
    }

    /** 执行请求并返回 data.id(Long), 断言 HTTP200 + code200 */
    protected long createAndGetId(String uri, Object body, String token) throws Exception {
        MvcResult result = postJson(uri, body, token)
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(200, json.path("code").asInt(), "创建失败, 响应体: " + json);
        return json.path("data").path("id").asLong();
    }
}
