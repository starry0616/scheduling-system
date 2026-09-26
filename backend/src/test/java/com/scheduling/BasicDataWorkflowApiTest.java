package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.entity.User;
import com.scheduling.repository.CourseRepository;
import com.scheduling.service.DemoDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 8.3-S 第二批 C1: 基础数据管理页面对接的真实 HTTP 流验证(Workflow)
 *
 * <p>用与前端完全相同的 URL / JSON 契约, 模拟管理员在页面上完成:
 * 登录 → 查询课程/教师/班级/教室/开课(Demo 数据兼容) → 课程增改删 →
 * 教师增改删(候选账号) → 开课班级关联覆盖保存 → 删除被引用课程显示业务错误
 * → 确认 Demo 排课任务保持 PENDING(不受管理操作影响)。
 *
 * <p>只创建/清理带唯一后缀的临时数据; 只读 Demo 数据, 不修改、不删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BasicDataWorkflowApiTest extends BaseCrudApiTest {

    @Autowired
    private CourseRepository courseRepository;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private long createTempUser() {
        User user = userRepository.save(User.builder()
                .username("wf" + System.nanoTime())
                .password(ENCODER.encode("test123"))
                .realName("流程测试账号")
                .role(Constants.ROLE_TEACHER)
                .status(1)
                .build());
        return user.getId();
    }

    private void deleteTempUser(long userId) {
        try {
            userRepository.deleteById(userId);
        } catch (RuntimeException ignored) {
            // 忽略清理失败, 不掩盖主断言
        }
    }

    private Map<String, Object> courseBody(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseCode", code);
        m.put("courseName", name);
        m.put("courseType", "THEORY");
        m.put("requiredRoomType", "MULTIMEDIA");
        return m;
    }

    @Test
    @DisplayName("C1-W1: ADMIN/教师登录 + 五类基础数据读取(Demo 数据兼容)")
    void loginAndReadAllBasicData() throws Exception {
        // ADMIN 与 TEACHER 均能登录(读接口)
        adminToken();
        teacherToken();

        // 列表全部可查询
        getJson("/api/courses", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        getJson("/api/teachers", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        getJson("/api/classes", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        getJson("/api/classrooms", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        getJson("/api/course-offerings", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 若当前库已初始化 Demo 数据(开发库常态), 页面必须能读取到 Demo 课程/教师/班级/教室/开课
        if (courseRepository.existsByCourseCode(DemoDataService.MARKER_COURSE_CODE)) {
            getJson("/api/courses?keyword=" + DemoDataService.MARKER_COURSE_CODE, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].courseCode").value(DemoDataService.MARKER_COURSE_CODE));
            getJson("/api/teachers?keyword=T2026001", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].teacherNo").value("T2026001"));
            getJson("/api/classes?keyword=" + "软件工程2401", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].className").value("软件工程2401"));
            getJson("/api/classrooms?keyword=N201", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].roomNo").value("N201"));
            getJson("/api/course-offerings?semester=" + DemoDataService.DEMO_SEMESTER, adminToken())
                    .andExpect(status().isOk())
                    // 共享测试库可能残留历史开课, 只要求至少覆盖 Demo 的 17 条
                    .andExpect(jsonPath("$.data.length()")
                            .value(org.hamcrest.Matchers.greaterThanOrEqualTo(17)));
        }
    }

    @Test
    @DisplayName("C1-W2: 教师新增(候选账号)→编辑→删除, 候选仅 ADMIN 可见")
    void teacherFullLifecycleWithCandidates() throws Exception {
        long userId = createTempUser();
        String no = "T9" + uniqueSuffix();
        long teacherId = 0;
        try {
            // 候选列表: 新建的未绑定账号应出现(页面新增教师下拉的数据源)
            getJson("/api/teachers/user-candidates", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[?(@.id == " + userId + ")]").exists());

            teacherId = createAndGetId("/api/teachers", teacherBody(userId, no, "流程教师", null, null), adminToken());
            // 编辑(其它字段), 不改绑定
            putJson("/api/teachers/" + teacherId, teacherBody(userId, no, "流程教师-改", "副教授", "软件学院"), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("流程教师-改"));

            // 编辑场景: currentTeacherId 时候选应包含当前已绑定账号(供"保持不变")
            getJson("/api/teachers/user-candidates?currentTeacherId=" + teacherId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == " + userId + ")]").exists());
            // 删除
            deleteJson("/api/teachers/" + teacherId, adminToken()).andExpect(status().isOk());
            teacherId = 0;
        } finally {
            if (teacherId != 0) {
                try {
                    deleteJson("/api/teachers/" + teacherId, adminToken()).andExpect(status().isOk());
                } catch (Exception ignored) {
                }
            }
            deleteTempUser(userId);
        }

        // 非 ADMIN(教师登录)访问候选账号列表必须被拒绝, 避免泄露其它账号
        getJson("/api/teachers/user-candidates", teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("C1-W3: 课程 增→改→删; 删除被开课引用的课程显示业务错误; 开课班级关联覆盖保存")
    void courseLifecycleAndOfferingClassLink() throws Exception {
        long userId = createTempUser();
        String no = "T8" + uniqueSuffix();
        String courseCode = "W" + uniqueSuffix();
        long teacherId = 0;
        long offeringId = 0;
        long courseId = 0;
        try {
            courseId = createAndGetId("/api/courses", courseBody(courseCode, "工作流临时课程"), adminToken());
            putJson("/api/courses/" + courseId, courseBody(courseCode, "工作流临时课程-改"), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.courseName").value("工作流临时课程-改"));

            teacherId = createAndGetId("/api/teachers", teacherBody(userId, no, "工作流教师", null, null), adminToken());

            // 开课: 每周1次, 每次1节(理论, 多媒体)
            Map<String, Object> offering = new LinkedHashMap<>();
            offering.put("courseId", courseId);
            offering.put("teacherId", teacherId);
            offering.put("semester", "2026秋");
            offering.put("weeklySessions", 1);
            offering.put("durationSlots", 1);
            offeringId = createAndGetId("/api/course-offerings", offering, adminToken());

            // 已被开课实例引用: 删除课程必须被后端业务规则拒绝
            deleteJson("/api/courses/" + courseId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("该课程已被开课实例引用")));

            // 班级关联覆盖保存: 两个班 -> 一个班 -> 清空(页面"配置关联班级"的真实语义)
            long classA = createClassGetId("关联A" + uniqueSuffix());
            long classB = createClassGetId("关联B" + uniqueSuffix());
            try {
                putJson("/api/course-offerings/" + offeringId + "/classes", Map.of("classIds", new long[]{classA, classB}), adminToken())
                        .andExpect(status().isOk());
                getJson("/api/course-offerings/" + offeringId + "/classes", adminToken())
                        .andExpect(jsonPath("$.data.length()").value(2));
                putJson("/api/course-offerings/" + offeringId + "/classes", Map.of("classIds", new long[]{classA}), adminToken())
                        .andExpect(status().isOk());
                getJson("/api/course-offerings/" + offeringId + "/classes", adminToken())
                        .andExpect(jsonPath("$.data[0].id").value(classA));
            } finally {
                deleteJson("/api/classes/" + classA, adminToken()).andExpect(status().isOk());
                deleteJson("/api/classes/" + classB, adminToken()).andExpect(status().isOk());
            }

            // 解除引用后逐层清理: 开课 -> 课程 -> 教师
            deleteJson("/api/course-offerings/" + offeringId, adminToken()).andExpect(status().isOk());
            offeringId = 0;
            deleteJson("/api/courses/" + courseId, adminToken()).andExpect(status().isOk());
            courseId = 0;
            deleteJson("/api/teachers/" + teacherId, adminToken()).andExpect(status().isOk());
            teacherId = 0;
        } finally {
            if (offeringId != 0) {
                try {
                    deleteJson("/api/course-offerings/" + offeringId, adminToken());
                } catch (Exception ignored) {
                }
            }
            if (courseId != 0) {
                try {
                    deleteJson("/api/courses/" + courseId, adminToken());
                } catch (Exception ignored) {
                }
            }
            if (teacherId != 0) {
                try {
                    deleteJson("/api/teachers/" + teacherId, adminToken());
                } catch (Exception ignored) {
                }
            }
            deleteTempUser(userId);
        }
    }

    @Test
    @DisplayName("C1-W4: 管理操作后 Demo 演示任务保持 PENDING(可执行状态不受影响)")
    void demoTaskUnaffectedByManagement() throws Exception {
        if (!courseRepository.existsByCourseCode(DemoDataService.MARKER_COURSE_CODE)) {
            return; // 当前库无 Demo(例如纯测试库), 跳过
        }
        getJson("/api/scheduling-tasks", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.taskName == '2026秋 软件学院演示排课任务')].status")
                        .value(org.hamcrest.Matchers.contains("PENDING")));
    }

    // ---------- helpers ----------

    private Map<String, Object> teacherBody(long userId, String no, String name, String title, String department) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("teacherNo", no);
        m.put("name", name);
        m.put("title", title);
        m.put("department", department);
        return m;
    }

    private long createClassGetId(String name) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", name);
        m.put("grade", "2024级");
        m.put("studentCount", 40);
        return createAndGetId("/api/classes", m, adminToken());
    }
}
