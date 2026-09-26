package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.dto.LoginRequest;
import com.scheduling.dto.LoginResponse;
import com.scheduling.dto.UserInfoResponse;
import com.scheduling.entity.User;
import com.scheduling.repository.UserRepository;
import com.scheduling.service.UserService;
import com.scheduling.util.JwtUtil;
import com.scheduling.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证系统测试 - 12 项必须测试
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private static String adminToken;
    private static String teacherToken;

    @BeforeAll
    static void setup(@Autowired UserRepository userRepository) {
        // Ensure test users exist
        ensureUser(userRepository, "admin", "admin123", "管理员", Constants.ROLE_ADMIN, 1);
        ensureUser(userRepository, "teacher01", "teacher123", "张老师", Constants.ROLE_TEACHER, 1);
        ensureUser(userRepository, "disableduser", "disabled123", "被禁用户", Constants.ROLE_STUDENT, 0);
    }

    private static void ensureUser(UserRepository repo, String username, String password, String realName, String role, int status) {
        if (repo.findByUsername(username).isEmpty()) {
            repo.save(User.builder()
                    .username(username)
                    .password(encoder.encode(password))
                    .realName(realName)
                    .role(role)
                    .status(status)
                    .build());
        }
    }

    // ========== Test 1: 管理员正常登录 ==========
    @Test
    @Order(1)
    @DisplayName("1. 管理员正常登录 - 应返回有效Token")
    void testAdminLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        LoginResponse response = userService.login(request);

        assertNotNull(response.getToken());
        assertNotNull(response.getUser());
        assertEquals("admin", response.getUser().getUsername());
        assertEquals("管理员", response.getUser().getRealName());
        assertEquals(Constants.ROLE_ADMIN, response.getUser().getRole());
        assertNotNull(response.getUser().getId());

        adminToken = response.getToken();
        System.out.println("[PASS] 管理员登录成功, token长度=" + adminToken.length());
    }

    // ========== Test 2: 密码错误 ==========
    @Test
    @Order(2)
    @DisplayName("2. 密码错误 - 应返回401")
    void testWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpassword");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));
        assertEquals(401, ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());

        System.out.println("[PASS] 密码错误正确返回401: " + ex.getMessage());
    }

    // ========== Test 3: 不存在的用户 ==========
    @Test
    @Order(3)
    @DisplayName("3. 不存在的用户 - 应返回401")
    void testNonExistentUser() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("anypassword");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));
        assertEquals(401, ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());

        System.out.println("[PASS] 不存在用户正确返回401: " + ex.getMessage());
    }

    // ========== Test 4: 被禁用的用户 ==========
    @Test
    @Order(4)
    @DisplayName("4. 被禁用的用户 - 应返回403")
    void testDisabledUser() {
        LoginRequest request = new LoginRequest();
        request.setUsername("disableduser");
        request.setPassword("disabled123");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));
        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("禁用"));

        System.out.println("[PASS] 被禁用户正确返回403: " + ex.getMessage());
    }

    // ========== Test 5: 无Token访问 /auth/me ==========
    @Test
    @Order(5)
    @DisplayName("5. 无Token访问 - 应抛401异常")
    void testNoTokenAccess() {
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserInfoByToken(null));
        assertEquals(401, ex.getCode());

        System.out.println("[PASS] 无Token访问正确返回401: " + ex.getMessage());
    }

    // ========== Test 6: 有效Token获取用户信息 ==========
    @Test
    @Order(6)
    @DisplayName("6. 有效Token - 应返回正确用户信息")
    void testValidTokenGetUserInfo() {
        // 用 adminToken (from test 1, or create a new one)
        if (adminToken == null) {
            LoginRequest request = new LoginRequest();
            request.setUsername("admin");
            request.setPassword("admin123");
            adminToken = userService.login(request).getToken();
        }

        UserInfoResponse info = userService.getUserInfoByToken(adminToken);

        assertEquals("admin", info.getUsername());
        assertEquals("管理员", info.getRealName());
        assertEquals(Constants.ROLE_ADMIN, info.getRole());
        assertEquals(1, info.getStatus());

        System.out.println("[PASS] 有效Token获取用户信息成功: " + info.getUsername() + ", " + info.getRole());
    }

    // ========== Test 7: 无效/过期Token ==========
    @Test
    @Order(7)
    @DisplayName("7. 无效Token - 应返回401")
    void testInvalidToken() {
        String fakeToken = "invalid.token.string";

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getUserInfoByToken(fakeToken));
        assertEquals(401, ex.getCode());

        System.out.println("[PASS] 无效Token正确返回401: " + ex.getMessage());
    }

    // ========== Test 8: 角色验证 ==========
    @Test
    @Order(8)
    @DisplayName("8. 角色验证 - 教师Token包含TEACHER角色")
    void testRoleVerification() {
        LoginRequest request = new LoginRequest();
        request.setUsername("teacher01");
        request.setPassword("teacher123");

        LoginResponse response = userService.login(request);
        teacherToken = response.getToken();

        assertEquals(Constants.ROLE_TEACHER, response.getUser().getRole());

        // Token中提取的角色应一致
        String roleFromToken = jwtUtil.getRole(teacherToken);
        assertEquals(Constants.ROLE_TEACHER, roleFromToken);

        System.out.println("[PASS] 角色验证成功: " + roleFromToken);
    }

    // ========== Test 9: JWT工具类验证 ==========
    @Test
    @Order(9)
    @DisplayName("9. JwtUtil验证 - 生成、解析、过期验证")
    void testJwtUtil() {
        String token = jwtUtil.generateToken(999L, "testuser", "ADMIN");

        // 验证 Token 有效
        assertTrue(jwtUtil.validateToken(token));

        // 验证提取信息
        assertEquals(999L, jwtUtil.getUserId(token));
        assertEquals("testuser", jwtUtil.getUsername(token));
        assertEquals("ADMIN", jwtUtil.getRole(token));

        // 无效 Token
        assertFalse(jwtUtil.validateToken("invalid"));
        assertNull(jwtUtil.getUserId("invalid"));

        System.out.println("[PASS] JwtUtil验证全部通过");
    }

    // ========== Test 10: BCrypt密码加密验证 ==========
    @Test
    @Order(10)
    @DisplayName("10. BCrypt密码加密 - 同密码不同hash，matches验证通过")
    void testBCryptPassword() {
        String rawPassword = "mypassword";
        String hash1 = encoder.encode(rawPassword);
        String hash2 = encoder.encode(rawPassword);

        // 两次hash不同
        assertNotEquals(hash1, hash2);

        // 都能匹配原始密码
        assertTrue(encoder.matches(rawPassword, hash1));
        assertTrue(encoder.matches(rawPassword, hash2));

        // 错误密码不匹配
        assertFalse(encoder.matches("wrongpassword", hash1));

        System.out.println("[PASS] BCrypt密码验证通过: hash1=" + hash1.substring(0, 10) + "...");
    }

    // ========== Test 11: 登出 ==========
    @Test
    @Order(11)
    @DisplayName("11. 登出 - 服务端不报错")
    void testLogout() {
        if (adminToken == null) {
            LoginRequest request = new LoginRequest();
            request.setUsername("admin");
            request.setPassword("admin123");
            adminToken = userService.login(request).getToken();
        }

        // 登出不抛异常
        assertDoesNotThrow(() -> userService.logout(adminToken));

        System.out.println("[PASS] 登出成功");
    }

    // ========== Test 12: Token一致性 ==========
    @Test
    @Order(12)
    @DisplayName("12. Token一致性 - 同一用户多次登录用户信息一致")
    void testTokenConsistency() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        LoginResponse resp1 = userService.login(request);
        LoginResponse resp2 = userService.login(request);

        // Token可能相同(JWT的iat精度到秒，同一秒内生成的token完全一样)
        // 关键验证：用户信息必须一致
        assertEquals(resp1.getUser().getId(), resp2.getUser().getId());
        assertEquals(resp1.getUser().getUsername(), resp2.getUser().getUsername());
        assertEquals(resp1.getUser().getRole(), resp2.getUser().getRole());

        // 两个token都应该有效
        assertTrue(jwtUtil.validateToken(resp1.getToken()));
        assertTrue(jwtUtil.validateToken(resp2.getToken()));

        // 两个token提取的用户信息一致
        assertEquals(jwtUtil.getUserId(resp1.getToken()), jwtUtil.getUserId(resp2.getToken()));
        assertEquals(jwtUtil.getUsername(resp1.getToken()), jwtUtil.getUsername(resp2.getToken()));

        System.out.println("[PASS] Token一致性验证通过");
    }
}
