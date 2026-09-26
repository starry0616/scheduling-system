package com.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.scheduling.common.Constants;
import com.scheduling.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开课实例基础管理 Web 层测试 (第3步第三阶段 3C-1)
 *
 * 覆盖: ADMIN 全 CRUD / TEACHER 只读 / 401 / 403 /
 *       引用课程/教师不存在 / 参数校验(学期空、每周次数、连续大节数) /
 *       isLabCourse 由 durationSlots>1 派生 / 列表 semester 过滤 / 删除成功。
 *
 * 自清理: 测试基座(course/teacher/temp user)与 offering 均在 finally 中逆序删除,
 *         不依赖数据库中已有 Course/Teacher 数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseOfferingApiTest extends BaseCrudApiTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // ---------- 数据构造 ----------

    /** 短唯一后缀, 保证各字段长度约束(如 course_code≤20 / semester≤20) */
    private String shortTag() {
        return String.valueOf(System.nanoTime()).substring(6);
    }

    /** 新建一个可绑定教师的临时用户(唯一), 返回 userId */
    private long createTempUser() {
        User user = userRepository.save(User.builder()
                .username("u" + shortTag())
                .password(ENCODER.encode("test123"))
                .realName("测试用户")
                .role(Constants.ROLE_TEACHER)
                .status(1)
                .build());
        return user.getId();
    }

    private Map<String, Object> courseBody(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseCode", code);
        m.put("courseName", name);
        m.put("courseType", "THEORY");
        m.put("requiredRoomType", "NORMAL");
        return m;
    }

    private Map<String, Object> teacherBody(long userId, String no, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("teacherNo", no);
        m.put("name", name);
        m.put("title", null);
        m.put("department", null);
        return m;
    }

    private Map<String, Object> offeringBody(Long courseId, Long teacherId, String semester,
                                             Integer weeklySessions, Integer durationSlots) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseId", courseId);
        m.put("teacherId", teacherId);
        m.put("semester", semester);
        m.put("weeklySessions", weeklySessions);
        m.put("durationSlots", durationSlots);
        return m;
    }

    // ---------- 清理 ----------

    private static class Fixture {
        long userId;
        long courseId;
        long teacherId;
        long offeringId;
    }

    /** 建 course+teacher 基座(通过真实接口), 失败时先清理临时 user */
    private Fixture createBase() throws Exception {
        Fixture fx = new Fixture();
        try {
            fx.userId = createTempUser();
            fx.courseId = createAndGetId("/api/courses",
                    courseBody("CO" + shortTag(), "Java程序设计"), adminToken());
            fx.teacherId = createAndGetId("/api/teachers",
                    teacherBody(fx.userId, "T" + shortTag(), "陈明"), adminToken());
            return fx;
        } catch (Exception e) {
            deleteTempUserQuietly(fx.userId);
            throw e;
        }
    }

    private void deleteTempUserQuietly(long userId) {
        try {
            userRepository.deleteById(userId);
        } catch (RuntimeException ignored) {
            // 忽略清理失败, 避免掩盖主断言
        }
    }

    private void tryDelete(String uri) {
        try {
            deleteJson(uri, adminToken());
        } catch (Exception ignored) {
        }
    }

    /** 逆序清理: offering → teacher → course → temp user */
    private void cleanup(Fixture fx) {
        if (fx == null) {
            return;
        }
        if (fx.offeringId != 0) {
            tryDelete("/api/course-offerings/" + fx.offeringId);
        }
        if (fx.teacherId != 0) {
            tryDelete("/api/teachers/" + fx.teacherId);
        }
        if (fx.courseId != 0) {
            tryDelete("/api/courses/" + fx.courseId);
        }
        if (fx.userId != 0) {
            deleteTempUserQuietly(fx.userId);
        }
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return json.path("data").path("id").asLong();
    }

    // ---------- 测试 ----------

    @Test
    @DisplayName("CourseOffering1: ADMIN创建(含isLabCourse派生)/列表/学期过滤/详情/TEACHER读列表/修改/删除")
    void fullCrudFlow() throws Exception {
        Fixture fx = createBase();
        try {
            // ---- ADMIN 创建成功: durationSlots=2 -> isLabCourse=true, 响应冗余课程/教师名 ----
            String semester = "2026秋" + shortTag();
            MvcResult created = postJson("/api/course-offerings",
                            offeringBody(fx.courseId, fx.teacherId, semester, 2, 2), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.courseId").value(fx.courseId))
                    .andExpect(jsonPath("$.data.courseName").value("Java程序设计"))
                    .andExpect(jsonPath("$.data.teacherId").value(fx.teacherId))
                    .andExpect(jsonPath("$.data.teacherName").value("陈明"))
                    .andExpect(jsonPath("$.data.semester").value(semester))
                    .andExpect(jsonPath("$.data.weeklySessions").value(2))
                    .andExpect(jsonPath("$.data.durationSlots").value(2))
                    .andExpect(jsonPath("$.data.isLabCourse").value(true))
                    .andReturn();
            long offeringId = readId(created);
            fx.offeringId = offeringId;

            // ---- ADMIN 查询详情 ----
            getJson("/api/course-offerings/" + offeringId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(offeringId))
                    .andExpect(jsonPath("$.data.courseId").value(fx.courseId))
                    .andExpect(jsonPath("$.data.semester").value(semester))
                    .andExpect(jsonPath("$.data.isLabCourse").value(true));

            // ---- ADMIN 查询列表: 学期精确过滤 ----
            getJson("/api/course-offerings", Map.of("semester", semester), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(fx.offeringId));

            // ---- TEACHER 查询列表成功(只读) ----
            getJson("/api/course-offerings", Map.of("semester", semester), teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(fx.offeringId));

            // ---- ADMIN 修改: durationSlots 1 -> isLabCourse 回退 false ----
            String newSemester = "2027春" + shortTag();
            putJson("/api/course-offerings/" + fx.offeringId,
                            offeringBody(fx.courseId, fx.teacherId, newSemester, 3, 1), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.semester").value(newSemester))
                    .andExpect(jsonPath("$.data.weeklySessions").value(3))
                    .andExpect(jsonPath("$.data.durationSlots").value(1))
                    .andExpect(jsonPath("$.data.isLabCourse").value(false));

            // ---- ADMIN 删除成功, 删除后再查 -> code404 ----
            deleteJson("/api/course-offerings/" + offeringId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            fx.offeringId = 0;

            getJson("/api/course-offerings/" + offeringId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("开课实例不存在")));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("CourseOffering2: 参数校验 -> HTTP400(学期为空/周次数为空或0/连续大节数为0或负)")
    void createValidationFails() throws Exception {
        // 学期为空
        postJson("/api/course-offerings", offeringBody(1L, 1L, "   ", 1, 1), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("学期不能为空")));

        // weekly_sessions 为空
        postJson("/api/course-offerings", offeringBody(1L, 1L, "2026春", null, 1), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("每周上课次数不能为空")));

        // weekly_sessions = 0
        postJson("/api/course-offerings", offeringBody(1L, 1L, "2026春", 0, 1), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("每周上课次数必须为正整数")));

        // duration_slots = 0
        postJson("/api/course-offerings", offeringBody(1L, 1L, "2026春", 1, 0), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("每次课连续占用大节数必须为正整数")));

        // duration_slots = -1
        postJson("/api/course-offerings", offeringBody(1L, 1L, "2026春", 1, -1), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("每次课连续占用大节数必须为正整数")));
    }

    @Test
    @DisplayName("CourseOffering3: 引用的课程不存在 -> code404")
    void createCourseNotFound() throws Exception {
        postJson("/api/course-offerings", offeringBody(99999999L, 99999999L, "2026春", 1, 1), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("引用的课程不存在")));
    }

    @Test
    @DisplayName("CourseOffering4: 引用的教师不存在 -> code404")
    void createTeacherNotFound() throws Exception {
        Fixture fx = new Fixture();
        try {
            fx.userId = createTempUser();
            fx.courseId = createAndGetId("/api/courses",
                    courseBody("CO" + shortTag(), "Java程序设计"), adminToken());
            // 课程真实存在, 但教师 99999999 不存在
            postJson("/api/course-offerings", offeringBody(fx.courseId, 99999999L, "2026春", 1, 1), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("引用的教师不存在")));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("CourseOffering5: 查询不存在的开课实例ID -> code404")
    void detailNotFound() throws Exception {
        getJson("/api/course-offerings/99999999", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("开课实例不存在")));
    }

    @Test
    @DisplayName("CourseOffering6: TEACHER 执行 新增/修改/删除 -> HTTP403")
    void teacherWriteForbidden() throws Exception {
        Map<String, Object> body = offeringBody(1L, 1L, "2026春", 1, 1);

        postJson("/api/course-offerings", body, teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        putJson("/api/course-offerings/1", body, teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        deleteJson("/api/course-offerings/1", teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("CourseOffering7: 未登录 查询/写 -> HTTP401")
    void noTokenReturns401() throws Exception {
        getJson("/api/course-offerings", null)
                .andExpect(status().isUnauthorized());

        postWithoutToken("/api/course-offerings", offeringBody(1L, 1L, "2026春", 1, 1))
                .andExpect(status().isUnauthorized());
    }
}
