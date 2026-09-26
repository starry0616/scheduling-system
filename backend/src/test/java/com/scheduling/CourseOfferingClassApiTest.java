package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.entity.User;
import com.scheduling.repository.CourseOfferingClassRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 开课实例-班级关联 Web 层测试 (第3步第三阶段 3C-2)
 *
 * 覆盖: ADMIN 覆盖式保存(先建后替/去重/清空)/查询顺序 / 引用班级不存在 code404 /
 *       offering 不存在 code404 / 参数校验(班级列表为空/null 元素) /
 *       权限(TEACHER 写 403, 未登录 401, TEACHER 只读 200) /
 *       删除 offering 级联清理关联(服务层) / 被引用的班级删除保护 code400。
 *
 * 自清理: 逆序删除(offering → class → teacher → course → temp user), 不依赖遗留数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseOfferingClassApiTest extends BaseCrudApiTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private CourseOfferingClassRepository courseOfferingClassRepository;

    // ---------- 数据构造 ----------

    private String shortTag() {
        return String.valueOf(System.nanoTime()).substring(6);
    }

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

    private Map<String, Object> offeringBody(Long courseId, Long teacherId, String semester) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseId", courseId);
        m.put("teacherId", teacherId);
        m.put("semester", semester);
        m.put("weeklySessions", 2);
        m.put("durationSlots", 1);
        return m;
    }

    private Map<String, Object> classBody(String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", name);
        m.put("grade", "2023级");
        m.put("studentCount", 40);
        m.put("department", null);
        return m;
    }

    private Map<String, Object> classesBody(List<Long> classIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classIds", classIds);
        return m;
    }

    // ---------- 清理 ----------

    private static class Fixture {
        long userId;
        long courseId;
        long teacherId;
        long offeringId;
        final List<Long> classIds = new ArrayList<>();
    }

    /** 建 course+teacher+offering 基座(通过真实接口), 失败时先清理临时 user */
    private Fixture createOfferingBase() throws Exception {
        Fixture fx = new Fixture();
        try {
            fx.userId = createTempUser();
            fx.courseId = createAndGetId("/api/courses",
                    courseBody("CO" + shortTag(), "Java程序设计"), adminToken());
            fx.teacherId = createAndGetId("/api/teachers",
                    teacherBody(fx.userId, "T" + shortTag(), "陈明"), adminToken());
            fx.offeringId = createAndGetId("/api/course-offerings",
                    offeringBody(fx.courseId, fx.teacherId, "2026秋" + shortTag()), adminToken());
            return fx;
        } catch (Exception e) {
            deleteTempUserQuietly(fx.userId);
            throw e;
        }
    }

    /** 新建一个班级, 返回班级ID */
    private long createClass(Fixture fx) throws Exception {
        long classId = createAndGetId("/api/classes", classBody("软工" + shortTag()), adminToken());
        fx.classIds.add(classId);
        return classId;
    }

    private void deleteTempUserQuietly(long userId) {
        try {
            userRepository.deleteById(userId);
        } catch (RuntimeException ignored) {
        }
    }

    private void tryDelete(String uri) {
        try {
            deleteJson(uri, adminToken());
        } catch (Exception ignored) {
        }
    }

    /** 逆序清理: offering → class → teacher → course → temp user */
    private void cleanup(Fixture fx) {
        if (fx == null) {
            return;
        }
        if (fx.offeringId != 0) {
            tryDelete("/api/course-offerings/" + fx.offeringId);
        }
        for (long classId : fx.classIds) {
            tryDelete("/api/classes/" + classId);
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

    // ---------- 测试 ----------

    @Test
    @DisplayName("CourseOfferingClass1: 覆盖式保存(先建2班/去重替换/清空)/查询顺序/TEACHER只读/删除offering级联清理")
    void replaceAndQueryFlow() throws Exception {
        Fixture fx = createOfferingBase();
        try {
            long c1 = createClass(fx);
            long c2 = createClass(fx);
            String base = "/api/course-offerings/" + fx.offeringId + "/classes";

            // ---- 初次关联 [c1, c2], 返回按关联顺序 ----
            putJson(base, classesBody(List.of(c1, c2)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("保存成功"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(c1))
                    .andExpect(jsonPath("$.data[1].id").value(c2));

            // ---- GET 查询: 顺序一致 ----
            getJson(base, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].className").isNotEmpty())
                    .andExpect(jsonPath("$.data[1].studentCount").value(40));

            // ---- TEACHER 只读(查询接口登录即可) ----
            getJson(base, teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2));

            // ---- 覆盖式替换为 [c2, c2, c3]: 去重 + 移除 c1 ----
            long c3 = createClass(fx);
            putJson(base, classesBody(List.of(c2, c2, c3)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(c2))
                    .andExpect(jsonPath("$.data[1].id").value(c3));

            // ---- 清空: [] ----
            putJson(base, classesBody(List.of()), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(0));
            getJson(base, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("CourseOfferingClass2: offering不存在/引用班级不存在 -> code404")
    void notFoundCases() throws Exception {
        Fixture fx = createOfferingBase();
        try {
            // ---- offering 不存在: GET/PUT -> code404 ----
            getJson("/api/course-offerings/99999999/classes", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("开课实例不存在")));
            putJson("/api/course-offerings/99999999/classes", classesBody(List.of(1L)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("开课实例不存在")));

            // ---- offering 存在, 引用班级不存在 -> code404, 且不产生任何半更新 ----
            String base = "/api/course-offerings/" + fx.offeringId + "/classes";
            putJson(base, classesBody(List.of(99999999L)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("引用的班级不存在")));
            getJson(base, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("CourseOfferingClass3: 参数校验(班级列表为null/含null元素) -> HTTP400")
    void validationCases() throws Exception {
        Fixture fx = createOfferingBase();
        try {
            String base = "/api/course-offerings/" + fx.offeringId + "/classes";

            // ---- classIds 字段缺失(null) -> Bean Validation 400 ----
            putJson(base, Map.of(), adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("班级列表不能为空")));

            // ---- 元素含 null -> 服务层业务校验 BusinessException(400), 走 Result.code=400 ----
            List<Long> withNull = new ArrayList<>();
            withNull.add(null);
            putJson(base, classesBody(withNull), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("关联班级ID不能为空")));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("CourseOfferingClass4: TEACHER 保存 -> HTTP403; 未登录 查询/保存 -> HTTP401")
    void permissionCases() throws Exception {
        Fixture fx = createOfferingBase();
        try {
            String base = "/api/course-offerings/" + fx.offeringId + "/classes";

            putJson(base, classesBody(List.of(1L)), teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            getJson("/api/course-offerings/99999999/classes", null)
                    .andExpect(status().isUnauthorized());
            putWithoutTokenJson(base, classesBody(List.of(1L)))
                    .andExpect(status().isUnauthorized());
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("CourseOfferingClass5: 关联的班级删除保护 code400; 删除offering级联清理关联(服务层)后可再删班级")
    void referencedClassDeleteProtectedAndCascade() throws Exception {
        Fixture fx = createOfferingBase();
        try {
            long c1 = createClass(fx);
            String base = "/api/course-offerings/" + fx.offeringId + "/classes";
            putJson(base, classesBody(List.of(c1)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // ---- 被开课实例关联的班级不可删除 -> code400 ----
            deleteJson("/api/classes/" + c1, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("开课实例")));

            // ---- 删除 offering: 服务层先清理关联行(对应 schema.sql CASCADE 意图) ----
            deleteJson("/api/course-offerings/" + fx.offeringId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            assertEquals(0,
                    courseOfferingClassRepository.findByCourseOfferingIdOrderByIdAsc(fx.offeringId).size(),
                    "删除开课实例后关联行应被清理");
            fx.offeringId = 0;

            // ---- 解除引用后班级可正常删除 ----
            deleteJson("/api/classes/" + c1, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            assertTrue(courseOfferingClassRepository.findByCourseOfferingIdOrderByIdAsc(fx.offeringId).isEmpty());
        } finally {
            cleanup(fx);
        }
    }

    /** 无 Token 的 PUT(用于 401 场景) */
    private org.springframework.test.web.servlet.ResultActions putWithoutTokenJson(String uri, Object body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(uri)
                .contextPath(CONTEXT)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }
}
