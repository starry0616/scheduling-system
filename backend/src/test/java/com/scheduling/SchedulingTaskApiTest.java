package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.User;
import com.scheduling.repository.SchedulingTaskClassroomRepository;
import com.scheduling.repository.SchedulingTaskCourseRepository;
import com.scheduling.repository.SchedulingTaskRepository;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 排课任务 Web 层测试 (第3步第三阶段 3C-3)
 *
 * 覆盖: 任务 CRUD/默认值/列表过滤 / 参数校验(Bean Validation) / PUT 忽略 status 与
 *       hardConstraintWeight(系统字段) / PENDING 修改成功 / 非 PENDING 修改与删除失败 /
 *       offering 范围(查询/覆盖保存/去重/清空/不存在/学期不一致/无半更新) /
 *       classroom 范围(查询/覆盖保存/去重/清空/不存在) /
 *       task 不存在 code404 / 删除任务级联清理两张关联表 /
 *       删除 offering、classroom 的任务引用保护(服务层, 无 DB FK) /
 *       权限 401/403 / 权限路径全覆盖。
 *
 * 说明: 本阶段无状态迁移接口, RUNNING/COMPLETED/FAILED 状态通过直接修改库(Repository)
 *       构造, 用于验证状态保护, 清理同样走 Repository 直删。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchedulingTaskApiTest extends BaseCrudApiTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private SchedulingTaskRepository schedulingTaskRepository;
    @Autowired
    private SchedulingTaskCourseRepository schedulingTaskCourseRepository;
    @Autowired
    private SchedulingTaskClassroomRepository schedulingTaskClassroomRepository;

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

    private Map<String, Object> classroomBody(String roomNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomNo", roomNo);
        m.put("building", "教学楼A");
        m.put("capacity", 80);
        m.put("roomType", "NORMAL");
        return m;
    }

    /** 最小任务体(缺省参数走系统默认值) */
    private Map<String, Object> taskBody(String taskName, String semester) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskName", taskName);
        m.put("semester", semester);
        return m;
    }

    private Map<String, Object> offeringScopeBody(List<Long> offeringIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseOfferingIds", offeringIds);
        return m;
    }

    private Map<String, Object> classroomScopeBody(List<Long> classroomIds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classroomIds", classroomIds);
        return m;
    }

    // ---------- 清理 ----------

    private static class Fixture {
        long userId;
        long courseId;
        long teacherId;
        final List<Long> offeringIds = new ArrayList<>();
        final List<Long> classroomIds = new ArrayList<>();
        final List<Long> taskIds = new ArrayList<>();
    }

    /** 建 course+teacher 基座(通过真实接口), 失败时先清理临时 user */
    private Fixture createBase() throws Exception {
        Fixture fx = new Fixture();
        try {
            fx.userId = createTempUser();
            fx.courseId = createAndGetId("/api/courses",
                    courseBody("CO" + shortTag(), "数据结构"), adminToken());
            fx.teacherId = createAndGetId("/api/teachers",
                    teacherBody(fx.userId, "T" + shortTag(), "陈明"), adminToken());
            return fx;
        } catch (Exception e) {
            deleteTempUserQuietly(fx.userId);
            throw e;
        }
    }

    private long createOffering(Fixture fx, String semester) throws Exception {
        long offeringId = createAndGetId("/api/course-offerings",
                offeringBody(fx.courseId, fx.teacherId, semester), adminToken());
        fx.offeringIds.add(offeringId);
        return offeringId;
    }

    private long createClassroom(Fixture fx) throws Exception {
        long classroomId = createAndGetId("/api/classrooms",
                classroomBody("RM" + shortTag()), adminToken());
        fx.classroomIds.add(classroomId);
        return classroomId;
    }

    private long createTask(Fixture fx, String taskName, String semester) throws Exception {
        long taskId = createAndGetId("/api/scheduling-tasks", taskBody(taskName, semester), adminToken());
        fx.taskIds.add(taskId);
        return taskId;
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

    /** 绕过状态保护直删任务(同时清两张关联表), 供非 PENDING 场景清理 */
    private void deleteTaskDirect(long taskId) {
        try {
            if (schedulingTaskRepository.findById(taskId).isEmpty()) {
                return;
            }
            schedulingTaskCourseRepository.deleteBySchedulingTaskId(taskId);
            schedulingTaskClassroomRepository.deleteBySchedulingTaskId(taskId);
            schedulingTaskRepository.deleteById(taskId);
        } catch (RuntimeException ignored) {
        }
    }

    /** 直接改库置任务状态(构造 RUNNING/COMPLETED/FAILED, 本阶段无状态迁移接口) */
    private void forceStatus(long taskId, String status) {
        schedulingTaskRepository.findById(taskId)
                .ifPresent(task -> {
                    task.setStatus(status);
                    schedulingTaskRepository.save(task);
                });
    }

    /** 逆序清理: task → classroom → offering → teacher → course → temp user */
    private void cleanup(Fixture fx) {
        if (fx == null) {
            return;
        }
        for (long taskId : fx.taskIds) {
            deleteTaskDirect(taskId);
        }
        for (long classroomId : fx.classroomIds) {
            tryDelete("/api/classrooms/" + classroomId);
        }
        for (long offeringId : fx.offeringIds) {
            tryDelete("/api/course-offerings/" + offeringId);
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
    @DisplayName("SchedulingTask1: 创建默认值/详情/TEACHER只读/列表过滤/PENDING修改成功/删除后404")
    void createDefaultsUpdateAndDelete() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            // ---- 创建(最小请求体): 状态 PENDING + 各参数默认值 + 系统字段为 null ----
            long taskId = createTask(fx, "春季排课", semester);
            getJson("/api/scheduling-tasks/" + taskId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.taskName").value("春季排课"))
                    .andExpect(jsonPath("$.data.semester").value(semester))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.weekCount").value(16))
                    .andExpect(jsonPath("$.data.maxInitialTemp").value(1000.0))
                    .andExpect(jsonPath("$.data.minInitialTemp").value(10.0))
                    .andExpect(jsonPath("$.data.minTemp").value(0.1))
                    .andExpect(jsonPath("$.data.coolingRate").value(0.95))
                    .andExpect(jsonPath("$.data.maxTempIterations").value(5000))
                    .andExpect(jsonPath("$.data.neighborsPerTemp").value(20))
                    .andExpect(jsonPath("$.data.maxRepairAttempts").value(3))
                    .andExpect(jsonPath("$.data.hardConstraintWeight").value(nullValue()))
                    .andExpect(jsonPath("$.data.randomSeed").value(nullValue()))
                    .andExpect(jsonPath("$.data.finishTime").value(nullValue()))
                    .andExpect(jsonPath("$.data.wTeacherPreference").value(50))
                    .andExpect(jsonPath("$.data.wCourseDistribution").value(30))
                    .andExpect(jsonPath("$.data.wStudentBalance").value(25))
                    .andExpect(jsonPath("$.data.wTeacherContinuous").value(30))
                    .andExpect(jsonPath("$.data.wStudentIdle").value(25))
                    .andExpect(jsonPath("$.data.wMorningEvening").value(15))
                    .andExpect(jsonPath("$.data.createTime").value(notNullValue()));

            // ---- TEACHER 只读(查询接口登录即可) ----
            getJson("/api/scheduling-tasks/" + taskId, teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // ---- 列表: semester + status 过滤 ----
            getJson("/api/scheduling-tasks?semester=" + semester + "&status=PENDING", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(taskId));

            // ---- PENDING 修改成功: 数值参数/权重/randomSeed 均生效 ----
            Map<String, Object> update = new LinkedHashMap<>(taskBody("春季排课V2", semester));
            update.put("weekCount", 18);
            update.put("coolingRate", 0.9);
            update.put("maxTempIterations", 8000);
            update.put("neighborsPerTemp", 30);
            update.put("randomSeed", 42L);
            update.put("wStudentBalance", 33);
            putJson("/api/scheduling-tasks/" + taskId, update, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"))
                    .andExpect(jsonPath("$.data.taskName").value("春季排课V2"))
                    .andExpect(jsonPath("$.data.weekCount").value(18))
                    .andExpect(jsonPath("$.data.coolingRate").value(0.9))
                    .andExpect(jsonPath("$.data.maxTempIterations").value(8000))
                    .andExpect(jsonPath("$.data.neighborsPerTemp").value(30))
                    .andExpect(jsonPath("$.data.randomSeed").value(42))
                    .andExpect(jsonPath("$.data.wStudentBalance").value(33))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));

            // ---- PENDING 删除成功 ----
            deleteJson("/api/scheduling-tasks/" + taskId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("删除成功"));
            // 删除后再次查询 -> code404
            getJson("/api/scheduling-tasks/" + taskId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("排课任务不存在")));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask2: 参数校验(Bean Validation 400)/PUT禁止改status与hardConstraintWeight")
    void paramValidationAndSystemFieldsProtected() throws Exception {
        Fixture fx = createBase();
        try {
            String semester = "2026春" + shortTag();

            // ---- 任务名称缺失 -> HTTP400 ----
            postJson("/api/scheduling-tasks", taskBody("", semester), adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("任务名称不能为空")));
            // ---- 学期超长 -> HTTP400 ----
            postJson("/api/scheduling-tasks",
                    taskBody("x", "2026-2027学年度秋季学期第一学期-太长"), adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("学期长度不能超过20")));
            // ---- weekCount=0 -> HTTP400 ----
            Map<String, Object> week0 = new LinkedHashMap<>(taskBody("t", semester));
            week0.put("weekCount", 0);
            postJson("/api/scheduling-tasks", week0, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("总周数必须大于等于1")));
            // ---- coolingRate 越界(0.0 与 1.0 均非法) -> HTTP400 ----
            Map<String, Object> rate0 = new LinkedHashMap<>(taskBody("t", semester));
            rate0.put("coolingRate", 0.0);
            postJson("/api/scheduling-tasks", rate0, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("降温系数必须大于0且小于1")));
            Map<String, Object> rate1 = new LinkedHashMap<>(taskBody("t", semester));
            rate1.put("coolingRate", 1.0);
            postJson("/api/scheduling-tasks", rate1, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("降温系数必须大于0且小于1")));
            // ---- maxInitialTemp=0(必须>0) -> HTTP400 ----
            Map<String, Object> temp0 = new LinkedHashMap<>(taskBody("t", semester));
            temp0.put("maxInitialTemp", 0.0);
            postJson("/api/scheduling-tasks", temp0, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("初始温度上限必须大于0")));
            // ---- 权重为负 -> HTTP400 ----
            Map<String, Object> wNeg = new LinkedHashMap<>(taskBody("t", semester));
            wNeg.put("wStudentIdle", -1);
            postJson("/api/scheduling-tasks", wNeg, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("权重不能为负")));
            // ---- maxRepairAttempts=-1 -> HTTP400 ----
            Map<String, Object> repairNeg = new LinkedHashMap<>(taskBody("t", semester));
            repairNeg.put("maxRepairAttempts", -1);
            postJson("/api/scheduling-tasks", repairNeg, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("冲突修复最大轮数必须大于等于0")));

            // ---- 合法创建后: PUT 夹带 status/hardConstraintWeight(系统字段)被忽略 ----
            long taskId = createTask(fx, "系统字段保护", semester);
            Map<String, Object> evil = new LinkedHashMap<>(taskBody("系统字段保护V2", semester));
            evil.put("weekCount", 17);
            evil.put("status", "RUNNING");
            evil.put("hardConstraintWeight", 123456L);
            putJson("/api/scheduling-tasks/" + taskId, evil, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.taskName").value("系统字段保护V2"))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.hardConstraintWeight").value(nullValue()));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask3: 非PENDING(RUNNING/COMPLETED/FAILED)禁止修改与删除")
    void nonPendingBlockedForUpdateAndDelete() throws Exception {
        Fixture fx = createBase();
        try {
            String semester = "2026夏" + shortTag();
            long taskId = createTask(fx, "状态保护", semester);
            for (String status : List.of("RUNNING", "COMPLETED", "FAILED")) {
                forceStatus(taskId, status);

                // 修改 -> code400
                putJson("/api/scheduling-tasks/" + taskId,
                        taskBody("状态保护改", semester), adminToken())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(400))
                        .andExpect(jsonPath("$.message").value(containsString("仅 PENDING 状态的任务允许修改")))
                        .andExpect(jsonPath("$.message").value(containsString(status)));

                // 删除 -> code400
                deleteJson("/api/scheduling-tasks/" + taskId, adminToken())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(400))
                        .andExpect(jsonPath("$.message").value(containsString("仅 PENDING 状态的任务允许删除")));

                // 改范围(offering/classroom)同样被拒 -> code400
                putJson("/api/scheduling-tasks/" + taskId + "/course-offerings",
                        offeringScopeBody(List.of()), adminToken())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(400))
                        .andExpect(jsonPath("$.message").value(containsString("仅 PENDING 状态的任务允许修改课程范围")));
                putJson("/api/scheduling-tasks/" + taskId + "/classrooms",
                        classroomScopeBody(List.of()), adminToken())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(400))
                        .andExpect(jsonPath("$.message").value(containsString("仅 PENDING 状态的任务允许修改教室范围")));
            }
            // 状态保护下 DB 记录未被改动(仍是原名称)
            assertEquals("状态保护", schedulingTaskRepository.findById(taskId)
                    .orElseThrow().getTaskName(), "非 PENDING 状态下 PUT 不应生效");
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask4: offering范围 查询/覆盖保存/去重/清空/不存在404/学期不一致/无半更新")
    void offeringScopeFlow() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            long o1 = createOffering(fx, semester);
            long o3 = createOffering(fx, semester);
            long o2 = createOffering(fx, "2025春" + shortTag()); // 跨学期
            long taskId = createTask(fx, "范围任务", semester);
            String scope = "/api/scheduling-tasks/" + taskId + "/course-offerings";

            // ---- 初始为空 ----
            getJson(scope, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(0));

            // ---- 覆盖保存 [o1, o1, o3]: 去重 + 保持首次顺序 ----
            putJson(scope, offeringScopeBody(List.of(o1, o1, o3)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("保存成功"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(o1))
                    .andExpect(jsonPath("$.data[0].courseName").value("数据结构"))
                    .andExpect(jsonPath("$.data[0].teacherName").isNotEmpty())
                    .andExpect(jsonPath("$.data[1].id").value(o3));

            // ---- GET 查询一致 ----
            getJson(scope, teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));

            // ---- 覆盖替换为 [o3]: 移除 o1 ----
            putJson(scope, offeringScopeBody(List.of(o3)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(o3));

            // ---- 引用不存在的 offering -> code404, 且不产生半更新(仍是 [o3]) ----
            putJson(scope, offeringScopeBody(List.of(o3, 99999999L)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("引用的开课实例不存在")));
            getJson(scope, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(o3));

            // ---- 跨学期 offering -> code400, 同样无半更新 ----
            putJson(scope, offeringScopeBody(List.of(o3, o2)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("禁止跨学期纳入任务")));
            getJson(scope, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

            // ---- 清空: [] ----
            putJson(scope, offeringScopeBody(List.of()), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
            getJson(scope, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));

            // ---- task 不存在 -> code404 ----
            getJson("/api/scheduling-tasks/99999999/course-offerings", adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("排课任务不存在")));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask5: 被任务引用的 offering 删除保护; 解除引用后可删除")
    void offeringDeleteProtection() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            long o1 = createOffering(fx, semester);
            long taskId = createTask(fx, "引用保护", semester);
            String scope = "/api/scheduling-tasks/" + taskId + "/course-offerings";
            putJson(scope, offeringScopeBody(List.of(o1)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // ---- 被排课任务引用 -> 删除 offering code400(服务层 existsBy, 无 DB FK) ----
            deleteJson("/api/course-offerings/" + o1, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("已被排课任务引用")));
            // offering 仍存在
            getJson("/api/course-offerings/" + o1, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // ---- 清空范围解除引用后, offering 可正常删除 ----
            putJson(scope, offeringScopeBody(List.of()), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            deleteJson("/api/course-offerings/" + o1, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask6: classroom范围 查询/覆盖保存/去重/清空/不存在404")
    void classroomScopeFlow() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            long c1 = createClassroom(fx);
            long c2 = createClassroom(fx);
            long c3 = createClassroom(fx);
            long taskId = createTask(fx, "教室池任务", semester);
            String scope = "/api/scheduling-tasks/" + taskId + "/classrooms";

            // ---- 初始为空 ----
            getJson(scope, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));

            // ---- 覆盖保存 [c1, c1, c2]: 去重 + 顺序 ----
            putJson(scope, classroomScopeBody(List.of(c1, c1, c2)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("保存成功"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(c1))
                    .andExpect(jsonPath("$.data[0].roomNo").isNotEmpty())
                    .andExpect(jsonPath("$.data[1].id").value(c2));

            // ---- 覆盖替换为 [c3] ----
            putJson(scope, classroomScopeBody(List.of(c3, c3)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(c3));

            // ---- 引用不存在的教室 -> code404, 无半更新 ----
            putJson(scope, classroomScopeBody(List.of(c3, 99999999L)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("引用的教室不存在")));
            getJson(scope, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));

            // ---- 清空 ----
            putJson(scope, classroomScopeBody(List.of()), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask7: 删除任务级联清理两关联表; 解除引用后 classroom 可删; 教室删除保护")
    void taskDeleteCascadeAndClassroomProtection() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            long offeringId = createOffering(fx, semester);
            long classroomId = createClassroom(fx);
            long taskId = createTask(fx, "级联任务", semester);
            putJson("/api/scheduling-tasks/" + taskId + "/course-offerings",
                    offeringScopeBody(List.of(offeringId)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            putJson("/api/scheduling-tasks/" + taskId + "/classrooms",
                    classroomScopeBody(List.of(classroomId)), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            assertEquals(1, schedulingTaskCourseRepository
                    .findBySchedulingTaskIdOrderByIdAsc(taskId).size(), "任务应纳入1个开课实例");
            assertEquals(1, schedulingTaskClassroomRepository
                    .findBySchedulingTaskIdOrderByIdAsc(taskId).size(), "任务应有1个可用教室");

            // ---- 教室删除保护: 被任务引用 -> code400 ----
            deleteJson("/api/classrooms/" + classroomId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("已被排课任务引用")));

            // ---- 删除 PENDING 任务: 服务层级联清理两张关联表(对应 schema.sql ON DELETE CASCADE) ----
            deleteJson("/api/scheduling-tasks/" + taskId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            assertEquals(0, schedulingTaskCourseRepository
                    .findBySchedulingTaskIdOrderByIdAsc(taskId).size(), "删除任务后 course 关联应清空");
            assertEquals(0, schedulingTaskClassroomRepository
                    .findBySchedulingTaskIdOrderByIdAsc(taskId).size(), "删除任务后 classroom 关联应清空");

            // ---- 引用解除后 offering/classroom 均可删除 ----
            deleteJson("/api/course-offerings/" + offeringId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            deleteJson("/api/classrooms/" + classroomId, adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask8: 范围请求校验(列表缺失/为null/含null元素) -> HTTP400")
    void scopeBodyValidation() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            long taskId = createTask(fx, "范围校验", semester);

            // ---- 字段缺失(JSON中无该键) -> Bean Validation 400 ----
            putJson("/api/scheduling-tasks/" + taskId + "/course-offerings", Map.of(), adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("开课实例列表不能为空")));
            putJson("/api/scheduling-tasks/" + taskId + "/classrooms", Map.of(), adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("教室列表不能为空")));

            // ---- 列表为 null -> Bean Validation 400 ----
            Map<String, Object> offeringNull = new LinkedHashMap<>();
            offeringNull.put("courseOfferingIds", null);
            putJson("/api/scheduling-tasks/" + taskId + "/course-offerings", offeringNull, adminToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("开课实例列表不能为空")));

            // ---- 元素含 null -> 服务层业务校验 BusinessException(400) ----
            List<Long> withNullOffering = new ArrayList<>();
            withNullOffering.add(null);
            putJson("/api/scheduling-tasks/" + taskId + "/course-offerings",
                    offeringScopeBody(withNullOffering), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("开课实例ID不能为空")));
            List<Long> withNullClassroom = new ArrayList<>();
            withNullClassroom.add(null);
            putJson("/api/scheduling-tasks/" + taskId + "/classrooms",
                    classroomScopeBody(withNullClassroom), adminToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("教室ID不能为空")));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @DisplayName("SchedulingTask9: 权限矩阵(未登录401 / TEACHER写403 / TEACHER只读200)")
    void permissionMatrix() throws Exception {
        Fixture fx = createBase();
        String semester = "2026秋" + shortTag();
        try {
            long taskId = createTask(fx, "权限任务", semester);

            // ---- 未登录: 查询与写均 401 ----
            getJson("/api/scheduling-tasks", null)
                    .andExpect(status().isUnauthorized());
            getJson("/api/scheduling-tasks/" + taskId, null)
                    .andExpect(status().isUnauthorized());
            postWithoutToken("/api/scheduling-tasks", taskBody("t", semester))
                    .andExpect(status().isUnauthorized());

            // ---- TEACHER 写操作 -> 403 ----
            putJson("/api/scheduling-tasks/" + taskId, taskBody("权限任务2", semester), teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
            postJson("/api/scheduling-tasks", taskBody("t3", semester), teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
            deleteJson("/api/scheduling-tasks/" + taskId, teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
            putJson("/api/scheduling-tasks/" + taskId + "/course-offerings",
                    offeringScopeBody(List.of()), teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));
            putJson("/api/scheduling-tasks/" + taskId + "/classrooms",
                    classroomScopeBody(List.of()), teacherToken())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            // ---- TEACHER 只读(列表/详情/两个范围) -> 200 ----
            getJson("/api/scheduling-tasks", teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            getJson("/api/scheduling-tasks/" + taskId + "/course-offerings", teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            getJson("/api/scheduling-tasks/" + taskId + "/classrooms", teacherToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } finally {
            cleanup(fx);
        }
    }
}
