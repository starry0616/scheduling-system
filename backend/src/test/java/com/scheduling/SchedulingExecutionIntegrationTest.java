package com.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.scheduling.algorithm.AnnealingResult;
import com.scheduling.algorithm.SimulatedAnnealing;
import com.scheduling.algorithm.SimulatedAnnealingParams;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.common.Constants;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.CourseOfferingClass;
import com.scheduling.entity.ResourceUnavailability;
import com.scheduling.entity.ScheduleEntry;
import com.scheduling.entity.ScheduleEntryClass;
import com.scheduling.entity.SchedulingResult;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.SchedulingTaskClassroom;
import com.scheduling.entity.SchedulingTaskCourse;
import com.scheduling.entity.Teacher;
import com.scheduling.entity.TeacherPreference;
import com.scheduling.entity.TimeSlot;
import com.scheduling.entity.User;
import com.scheduling.exception.BusinessException;
import com.scheduling.execution.SchedulingExecutionStore;
import com.scheduling.execution.SchedulingProblemAssembler;
import com.scheduling.repository.ClassroomRepository;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingClassRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.ResourceUnavailabilityRepository;
import com.scheduling.repository.ScheduleEntryClassRepository;
import com.scheduling.repository.ScheduleEntryRepository;
import com.scheduling.repository.SchedulingResultRepository;
import com.scheduling.repository.SchedulingTaskClassroomRepository;
import com.scheduling.repository.SchedulingTaskCourseRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import com.scheduling.repository.TeacherPreferenceRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.repository.TimeSlotRepository;
import com.scheduling.repository.UserRepository;
import com.scheduling.service.SchedulingTaskService;
import com.scheduling.vo.SchedulingTaskData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 阶段六 排课执行链路集成测试(真实 MySQL)
 *
 * <p>所有测试写真实数据库(复用既有 test 库与 admin/teacher01 账号约定)。
 * 每个场景使用 uniqueSuffix() 独立建数据, 互不依赖、顺序无关。
 *
 * <p>覆盖 16 项: 全链路持久化 / 状态机 / 重复执行防护 / 权重回填 /
 * 不可行数据(候选为空、每周课次超限) / 时长连续课 / 合班 /
 * 三类资源无冲突 / 教师偏好软约束 / 三类资源不可用过滤 /
 * RBAC / 系统异常落 FAILED / 保存失败整体回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchedulingExecutionIntegrationTest extends BaseCrudApiTest {

    private static final String SEMESTER = "2026秋";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // ---- 默认软约束权重(SchedulingTask @Builder.Default 值) ----
    private static final int[] DEFAULT_WEIGHTS = {50, 30, 25, 30, 25, 15};

    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ClazzRepository clazzRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private CourseOfferingRepository offeringRepository;
    @Autowired
    private CourseOfferingClassRepository offeringClassRepository;
    @Autowired
    private SchedulingTaskRepository taskRepository;
    @Autowired
    private SchedulingTaskCourseRepository taskCourseRepository;
    @Autowired
    private SchedulingTaskClassroomRepository taskClassroomRepository;
    @Autowired
    private ResourceUnavailabilityRepository unavailabilityRepository;
    @Autowired
    private TeacherPreferenceRepository preferenceRepository;
    @Autowired
    private SchedulingResultRepository resultRepository;
    @Autowired
    private ScheduleEntryRepository entryRepository;
    @Autowired
    private ScheduleEntryClassRepository entryClassRepository;

    @Autowired
    private SchedulingTaskService schedulingTaskService;
    @Autowired
    private SchedulingExecutionStore store;
    @Autowired
    private SchedulingProblemAssembler assembler;

    /** day-period -> slotId(当前已确保 5天×5节 时间轴存在) */
    private Map<String, Long> slotIds;
    private List<TimeSlot> timeSlots;

    // ==================================================================
    // 测试数据基础设施
    // ==================================================================

    @BeforeEach
    void ensureTimeSlotAxis() {
        slotIds = new LinkedHashMap<>();
        String[] starts = {"08:00", "10:00", "14:00", "16:00", "19:00"};
        String[] ends = {"09:40", "11:40", "15:40", "17:40", "20:40"};
        for (int day = 1; day <= 5; day++) {
            for (int p = 1; p <= 5; p++) {
                final int d = day;
                final int period = p;
                Optional<TimeSlot> existing = timeSlotRepository.findByDayOfWeekAndPeriod(d, period);
                TimeSlot slot = existing.orElseGet(() -> timeSlotRepository.save(TimeSlot.builder()
                        .dayOfWeek(d)
                        .period(period)
                        .startTime(starts[period - 1])
                        .endTime(ends[period - 1])
                        .build()));
                slotIds.put(day + ":" + p, slot.getId());
            }
        }
        timeSlots = timeSlotRepository.findAllByOrderByDayOfWeekAscPeriodAsc();
    }

    private long slot(int day, int period) {
        return slotIds.get(day + ":" + period);
    }

    private List<Long> allSlotIds() {
        return timeSlots.stream().map(TimeSlot::getId).toList();
    }

    // ---------- 主数据构建 ----------

    private long saveUser(String suffix, String role) {
        return userRepository.save(User.builder()
                .username("u_" + role.toLowerCase() + "_" + suffix)
                .password(ENCODER.encode("pass123"))
                .realName("教师" + suffix)
                .role(role)
                .status(1)
                .build()).getId();
    }

    private long saveTeacher(long userId, String suffix) {
        return teacherRepository.save(Teacher.builder()
                .userId(userId)
                .teacherNo("T" + suffix)
                .name("教师" + suffix)
                .build()).getId();
    }

    private long saveClazz(String suffix, int studentCount) {
        return clazzRepository.save(Clazz.builder()
                .className("班" + suffix)
                .grade("2026级")
                .studentCount(studentCount)
                .department("测试学院")
                .build()).getId();
    }

    private long saveCourse(String suffix, String courseType, String requiredRoomType) {
        return courseRepository.save(Course.builder()
                .courseCode("C" + suffix)
                .courseName("课程" + suffix)
                .courseType(courseType)
                .requiredRoomType(requiredRoomType)
                .build()).getId();
    }

    private long saveClassroom(String suffix, int capacity, String roomType) {
        return classroomRepository.save(Classroom.builder()
                .roomNo("R" + suffix)
                .building("测试楼")
                .capacity(capacity)
                .roomType(roomType)
                .build()).getId();
    }

    private long saveOffering(long courseId, long teacherId, String suffix, int weekly, int duration) {
        return offeringRepository.save(CourseOffering.builder()
                .courseId(courseId)
                .teacherId(teacherId)
                .semester(SEMESTER)
                .weeklySessions(weekly)
                .durationSlots(duration)
                .build()).getId();
    }

    private void offeringClasses(long offeringId, long... classIds) {
        List<CourseOfferingClass> rows = new ArrayList<>();
        for (long classId : classIds) {
            rows.add(CourseOfferingClass.builder()
                    .courseOfferingId(offeringId)
                    .classId(classId)
                    .build());
        }
        offeringClassRepository.saveAll(rows);
    }

    /** 建排课任务并写入范围(开课实例 + 教室池)。w/SA 参数均走实体默认值。 */
    private long saveTask(String suffix, List<Long> offeringIds, List<Long> classroomIds,
                          Long seed, Double coolingRate) {
        return saveTaskInternal(suffix, offeringIds, classroomIds, seed, coolingRate);
    }

    private long saveTaskInternal(String suffix, List<Long> offeringIds, List<Long> classroomIds,
                                  Long seed, Double coolingRate) {
        SchedulingTask task = taskRepository.save(SchedulingTask.builder()
                .taskName("排课-阶段六-" + suffix)
                .semester(SEMESTER)
                .randomSeed(seed)
                .coolingRate(coolingRate == null ? Constants.DEFAULT_COOLING_RATE : coolingRate)
                .createTime(LocalDateTime.now())
                .build());
        List<SchedulingTaskCourse> courseLinks = new ArrayList<>();
        for (Long offeringId : offeringIds) {
            courseLinks.add(SchedulingTaskCourse.builder()
                    .schedulingTaskId(task.getId())
                    .courseOfferingId(offeringId)
                    .build());
        }
        taskCourseRepository.saveAll(courseLinks);
        List<SchedulingTaskClassroom> roomLinks = new ArrayList<>();
        for (Long classroomId : classroomIds) {
            roomLinks.add(SchedulingTaskClassroom.builder()
                    .schedulingTaskId(task.getId())
                    .classroomId(classroomId)
                    .build());
        }
        taskClassroomRepository.saveAll(roomLinks);
        return task.getId();
    }

    private void blockSlots(String resourceType, long resourceId, List<Long> blockedSlotIds, String reason) {
        List<ResourceUnavailability> rows = new ArrayList<>();
        for (Long slotId : blockedSlotIds) {
            rows.add(ResourceUnavailability.builder()
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .timeSlotId(slotId)
                    .reason(reason)
                    .build());
        }
        unavailabilityRepository.saveAll(rows);
    }

    private void savePreference(long teacherId, int level, List<Long> slotIdList) {
        List<TeacherPreference> rows = new ArrayList<>();
        for (Long slotId : slotIdList) {
            rows.add(TeacherPreference.builder()
                    .teacherId(teacherId)
                    .timeSlotId(slotId)
                    .preferenceLevel(level)
                    .build());
        }
        preferenceRepository.saveAll(rows);
    }

    // ---------- 执行辅助 ----------

    /** 跑一次 run 并断言 HTTP200 + code200, 返回 data */
    private JsonNode runOk(long taskId) throws Exception {
        MvcResult result = performRun(taskId, adminToken());
        JsonNode body = toJson(result);
        assertEquals(200, body.path("code").asInt(), "run 应成功, 响应: " + body);
        return body.path("data");
    }

    /** 跑一次 run, 断言 HTTP 状态与业务 code 后返回完整响应体 */
    private JsonNode runExpectCode(long taskId, String token, int httpStatus, int businessCode) throws Exception {
        MvcResult result = performRun(taskId, token);
        assertEquals(httpStatus, result.getResponse().getStatus(), "HTTP 状态不符合预期");
        JsonNode body = toJson(result);
        assertEquals(businessCode, body.path("code").asInt(), "业务 code 不符合预期, 响应: " + body);
        return body;
    }

    private MvcResult performRun(long taskId, String token) throws Exception {
        // 与既有 Auth 测试一致: MockMvc 已自动应用 context-path=/api, URI 需写全路径
        MockHttpServletRequestBuilder builder = post("/api/scheduling-tasks/" + taskId + "/run")
                .contextPath(CONTEXT);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private JsonNode toJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private SchedulingTask task(long id) {
        return taskRepository.findById(id).orElseThrow();
    }

    private List<SchedulingResult> resultsOf(long taskId) {
        return resultRepository.findByTaskId(taskId).stream().toList();
    }

    private List<ScheduleEntry> entriesOf(long taskId) {
        return entryRepository.findByTaskIdOrderByIdAsc(taskId);
    }

    /** W_hard = floor(SoftMax)+1 的复算(与 SchedulingProblem 推导公式一致, 默认权重) */
    private long expectedHardWeight(int unitCount, int teacherCount, int classCount, int maxDuration) {
        double softMax = DEFAULT_WEIGHTS[0] * (double) unitCount * maxDuration
                + DEFAULT_WEIGHTS[1] * (double) unitCount * 4
                + DEFAULT_WEIGHTS[2] * 125 * (double) classCount
                + DEFAULT_WEIGHTS[3] * 10 * (double) teacherCount
                + DEFAULT_WEIGHTS[4] * 125 * (double) classCount
                + DEFAULT_WEIGHTS[5] * (double) unitCount * maxDuration;
        return (long) Math.floor(softMax) + 1;
    }

    /** 任务 SA 参数(与 ExecutionService.toParams 相同的缺省回落) */
    private SimulatedAnnealingParams paramsOf(SchedulingTask task) {
        return new SimulatedAnnealingParams(
                task.getMaxInitialTemp() == null ? 1000.0 : task.getMaxInitialTemp(),
                task.getMinInitialTemp() == null ? 10.0 : task.getMinInitialTemp(),
                task.getMinTemp() == null ? 0.1 : task.getMinTemp(),
                task.getCoolingRate() == null ? 0.95 : task.getCoolingRate(),
                task.getMaxTempIterations() == null ? 5000 : task.getMaxTempIterations(),
                task.getNeighborsPerTemp() == null ? 20 : task.getNeighborsPerTemp(),
                task.getMaxRepairAttempts() == null ? 3 : task.getMaxRepairAttempts());
    }

    // ==================================================================
    // 覆盖项 1/3/4/5/6/7/8: 全链路 happy path
    // ==================================================================

    @Test
    @DisplayName("覆盖1/3/4/5: 完整链路(任务→数据→算法→结果/条目/班级关联持久化 + 权重回填)")
    void happyPath_fullChainPersists() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long userB = saveUser(s + "_b", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long teacherB = saveTeacher(userB, s + "B");
        long k1 = saveClazz(s + "K1", 30);
        long k2 = saveClazz(s + "K2", 40);
        long courseTheoryA = saveCourse(s + "TA", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long courseTheoryB = saveCourse(s + "TB", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long courseLab = saveCourse(s + "LB", Constants.COURSE_LAB, Constants.ROOM_LAB);
        long courseMulti = saveCourse(s + "MU", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);

        long roomNormal = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long roomMedia = saveClassroom(s + "M", 200, Constants.ROOM_MULTIMEDIA);
        long roomLab = saveClassroom(s + "L", 200, Constants.ROOM_LAB);

        // o1 每周2次理论(教师A/班K1), o2 每周2次(教师B/班K2),
        // o3 每周1次连续2节实验(教师A/班K2), o4 每周1次合班(教师B/班K1+K2)
        long o1 = saveOffering(courseTheoryA, teacherA, s + "1", 2, 1);
        long o2 = saveOffering(courseTheoryB, teacherB, s + "2", 2, 1);
        long o3 = saveOffering(courseLab, teacherA, s + "3", 1, 2);
        long o4 = saveOffering(courseMulti, teacherB, s + "4", 1, 1);
        offeringClasses(o1, k1);
        offeringClasses(o2, k2);
        offeringClasses(o3, k2);
        offeringClasses(o4, k1, k2);

        // 6 个单元 = 2(o1) + 2(o2) + 1(o3) + 1(o4); 教师2/班级2/最大时长2
        long taskId = saveTask(s, List.of(o1, o2, o3, o4),
                List.of(roomNormal, roomMedia, roomLab), null, null);

        JsonNode data = runOk(taskId);

        assertEquals(Constants.TASK_COMPLETED, data.path("status").asText());
        assertEquals("FEASIBLE", data.path("outcome").asText());
        assertTrue(data.path("feasible").asBoolean());
        assertEquals(0, data.path("hardViolation").asInt());
        assertTrue(data.path("softPenalty").asDouble() >= 0);
        assertTrue(data.path("energy").asDouble() > 0);
        assertTrue(data.path("iterations").asInt() > 0);
        assertTrue(data.path("runtimeMs").asLong() >= 0);
        assertTrue(data.path("resultId").asLong() > 0);
        assertEquals(expectedHardWeight(6, 2, 2, 2), data.path("hardConstraintWeight").asLong());

        // ---- 任务侧 ----
        SchedulingTask task = task(taskId);
        assertEquals(Constants.TASK_COMPLETED, task.getStatus());
        assertNotNull(task.getFinishTime());
        assertEquals(expectedHardWeight(6, 2, 2, 2), task.getHardConstraintWeight());

        // ---- 结果 1:1 ----
        List<SchedulingResult> results = resultsOf(taskId);
        assertEquals(1, results.size());
        SchedulingResult result = results.get(0);
        assertEquals(0, result.getHardViolationCount());
        assertTrue(result.getIterationCount() > 0);
        assertTrue(result.getTotalNeighborEvals() > 0);
        assertTrue(result.getExecutionTimeMs() >= 0);
        assertNotNull(result.getIterationHistory());
        assertTrue(result.getIterationHistory().startsWith("["));
        assertNotNull(result.getFinishTime());

        // ---- 条目: 每单元一行, 与 offering 每周课次一致 ----
        List<ScheduleEntry> entries = entriesOf(taskId);
        assertEquals(6, entries.size());
        Map<Long, Integer> entryCountByOffering = new HashMap<>();
        for (ScheduleEntry e : entries) {
            entryCountByOffering.merge(e.getCourseOfferingId(), 1, Integer::sum);
        }
        assertEquals(2, entryCountByOffering.get(o1));
        assertEquals(2, entryCountByOffering.get(o2));
        assertEquals(1, entryCountByOffering.get(o3));
        assertEquals(1, entryCountByOffering.get(o4));

        // ---- 班级关联: 每班一行(合班 offering 两行) ----
        List<ScheduleEntryClass> classRows = entryClassRepository.findByScheduleEntryIdInOrderByIdAsc(
                entries.stream().map(ScheduleEntry::getId).toList());
        // o1 两班次 + o2 两班次 + o3 一班次 + o4(合班)两班次 = 7 行班级关联
        assertEquals(7, classRows.size());
        Map<Long, Set<Long>> classesByEntry = groupClassesByEntry(classRows);
        // o4 合班条目覆盖 k1+k2
        long combinedEntryId = entries.stream()
                .filter(e -> e.getCourseOfferingId() == o4)
                .map(ScheduleEntry::getId)
                .findFirst().orElseThrow();
        assertEquals(Set.of(k1, k2), classesByEntry.get(combinedEntryId));

        // ---- 排课语义: 连续占用 + 教师/班级/教室三类资源零冲突 ----
        assertNoOverlapsAndContinuous(taskId);
    }

    /** 校验连续性 + 教师/班级/教室零时间重叠 */
    private void assertNoOverlapsAndContinuous(long taskId) {
        List<ScheduleEntry> entries = entriesOf(taskId);
        List<Long> entryIds = entries.stream().map(ScheduleEntry::getId).toList();
        Map<Long, Set<Long>> classesByEntry = groupClassesByEntry(
                entryClassRepository.findByScheduleEntryIdInOrderByIdAsc(entryIds));
        Map<Long, TimeSlot> slotById = new HashMap<>();
        for (TimeSlot ts : timeSlots) {
            slotById.put(ts.getId(), ts);
        }
        Set<String> occupied = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (ScheduleEntry entry : entries) {
            TimeSlot start = slotById.get(entry.getTimeSlotId());
            assertNotNull(start, "条目引用了不存在的时间段 entryId=" + entry.getId());
            // 连续时长占用的下一节必须同日相邻存在
            for (int step = 0; step < durationOf(entry.getCourseOfferingId()); step++) {
                TimeSlot current = slotById.get(slotIds.get(start.getDayOfWeek() + ":" + (start.getPeriod() + step)));
                assertNotNull(current,
                        "时长跨节不连续: entryId=" + entry.getId() + " day=" + start.getDayOfWeek()
                                + " period=" + start.getPeriod() + " step=" + step);
                assertEquals(start.getDayOfWeek(), current.getDayOfWeek(), "跨天连排非法");
                mark(occupied, duplicates, "TEACHER:" + entry.getTeacherId() + ":" + current.getId(),
                        entry.getId());
                mark(occupied, duplicates, "ROOM:" + entry.getClassroomId() + ":" + current.getId(),
                        entry.getId());
                for (Long classId : classesByEntry.getOrDefault(entry.getId(), Set.of())) {
                    mark(occupied, duplicates, "CLASS:" + classId + ":" + current.getId(), entry.getId());
                }
            }
        }
        assertTrue(duplicates.isEmpty(),
                "存在硬约束时间重叠(重复的 教师/班级/教室×时间段): " + duplicates);
    }

    private void mark(Set<String> occupied, Set<String> duplicates, String key, long entryId) {
        if (!occupied.add(key)) {
            duplicates.add(key + " (entryId=" + entryId + ")");
        }
    }

    private int durationOf(long offeringId) {
        return offeringRepository.findById(offeringId)
                .map(CourseOffering::getDurationSlots)
                .orElse(1);
    }

    private Map<Long, Set<Long>> groupClassesByEntry(List<ScheduleEntryClass> rows) {
        Map<Long, Set<Long>> map = new HashMap<>();
        for (ScheduleEntryClass row : rows) {
            map.computeIfAbsent(row.getScheduleEntryId(), k -> new LinkedHashSet<>()).add(row.getClassId());
        }
        return map;
    }

    // ==================================================================
    // 覆盖2/16: 状态机 + 事务原子性
    // ==================================================================

    @Test
    @DisplayName("覆盖2: 状态机 PENDING→RUNNING→COMPLETED + 结果落库后任务置完成")
    void stateMachine_pendingRunningCompleted() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 2, 1);
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), 42L, null);

        // 事务A: 认领 → RUNNING, 且此刻无任何结果
        SchedulingTask running = store.claimRunning(taskId);
        assertEquals(Constants.TASK_RUNNING, running.getStatus());
        assertEquals(Constants.TASK_RUNNING, task(taskId).getStatus());
        assertTrue(resultsOf(taskId).isEmpty(), "RUNNING 阶段不应存在结果行");
        assertTrue(entriesOf(taskId).isEmpty(), "RUNNING 阶段不应存在条目");

        // 事务外(模拟算法耗时区段): 读取数据、组装、退火 —— 全部不落库
        SchedulingTaskData data = schedulingTaskService.getSchedulingTaskData(taskId);
        SchedulingProblem problem = assembler.assemble(data, Map.of(), Map.of(), Map.of(), Map.of());
        assertEquals(2, problem.getUnitCount(), "weeklySessions=2 应展开为 2 个单元");
        AnnealingResult result = SimulatedAnnealing.run(problem, paramsOf(task(taskId)), 42L);
        assertTrue(result.isFeasible());

        // 事务B: 结果一次性落库 + COMPLETED
        Long resultId = store.persistCompleted(taskId, problem, result, 55L, "[]");
        assertTrue(resultId > 0);
        SchedulingTask completed = task(taskId);
        assertEquals(Constants.TASK_COMPLETED, completed.getStatus());
        assertNotNull(completed.getFinishTime());
        assertEquals(expectedHardWeight(2, 1, 1, 1), completed.getHardConstraintWeight());
        assertEquals(1, resultsOf(taskId).size());
        assertEquals(2, entriesOf(taskId).size());
    }

    @Test
    @DisplayName("覆盖16: 保存失败时结果/条目/班级关联整体回滚, 无任何半套结果")
    void persistFailure_rollsBackWithoutPartialResults() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 2, 1);
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), 7L, null);

        store.claimRunning(taskId);

        // 模拟"算法运行期间任务被并发改为非 RUNNING"(可视为保存时的外部竞争)
        SchedulingTask changed = task(taskId);
        changed.setStatus(Constants.TASK_FAILED);
        changed.setFinishTime(LocalDateTime.now());
        taskRepository.save(changed);

        SchedulingTaskData data = schedulingTaskService.getSchedulingTaskData(taskId);
        SchedulingProblem problem = assembler.assemble(data, Map.of(), Map.of(), Map.of(), Map.of());
        AnnealingResult result = SimulatedAnnealing.run(problem, paramsOf(task(taskId)), 7L);

        // persistCompleted 中途状态校验失败 → IllegalStateException → 整个事务必须回滚
        IllegalStateException expected = assertThrows(IllegalStateException.class,
                () -> store.persistCompleted(taskId, problem, result, 1L, "[]"));
        assertTrue(expected.getMessage().contains("无法置为 COMPLETED"));

        // 半套结果绝不能残留
        assertTrue(resultsOf(taskId).isEmpty(), "保存失败后 result 必须回滚");
        assertTrue(entriesOf(taskId).isEmpty(), "保存失败后 entry 必须回滚");
        assertEquals(Constants.TASK_FAILED, task(taskId).getStatus(), "任务保持外部状态不变");

        // markFailed 对已是 FAILED 的任务是安全空操作
        store.markFailed(taskId, "并发竞争导致的模拟保存失败");
        assertEquals(Constants.TASK_FAILED, task(taskId).getStatus());
    }

    // ==================================================================
    // 覆盖9/11: 不可行数据预检
    // ==================================================================

    @Test
    @DisplayName("覆盖9(候选为空): 教师全部时间段不可用 → INFEASIBLE_DATA, 任务 FAILED 且无结果")
    void infeasible_noCandidatesWhenTeacherFullyBlocked() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);
        blockSlots(Constants.RESOURCE_TEACHER, teacherA, allSlotIds(), "教师全周出差");
        long taskId = saveTask(s, List.of(offering), List.of(room), null, null);

        JsonNode body = runExpectCode(taskId, adminToken(), 200, 400);
        assertTrue(body.path("message").asText().contains("INFEASIBLE_DATA"),
                "应返回 INFEASIBLE_DATA, 实际: " + body);

        assertEquals(Constants.TASK_FAILED, task(taskId).getStatus());
        assertNotNull(task(taskId).getFinishTime());
        assertTrue(resultsOf(taskId).isEmpty());
        assertTrue(entriesOf(taskId).isEmpty());
    }

    @Test
    @DisplayName("覆盖11: weeklySessions 超过可用工作日 → INFEASIBLE_DATA")
    void infeasible_weeklySessionsExceedAvailableDays() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 6, 1); // 6 > 5 天
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), null, null);

        JsonNode body = runExpectCode(taskId, adminToken(), 200, 400);
        String message = body.path("message").asText();
        assertTrue(message.contains("INFEASIBLE_DATA"), "实际: " + body);
        assertTrue(message.contains("每周课次数超过可用工作日数"), "实际: " + message);
        assertEquals(Constants.TASK_FAILED, task(taskId).getStatus());
        assertTrue(resultsOf(taskId).isEmpty());
    }

    @Test
    @DisplayName("覆盖10(候选为空): 教室全部时间不可用 → 无合法(教室,时间)候选")
    void infeasible_noCandidatesWhenClassroomFullyBlocked() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);
        blockSlots(Constants.RESOURCE_CLASSROOM, room, allSlotIds(), "教室装修");
        long taskId = saveTask(s, List.of(offering), List.of(room), null, null);

        JsonNode body = runExpectCode(taskId, adminToken(), 200, 400);
        assertTrue(body.path("message").asText().contains("INFEASIBLE_DATA"),
                "应返回 INFEASIBLE_DATA, 实际: " + body);
        assertEquals(Constants.TASK_FAILED, task(taskId).getStatus());
        assertTrue(resultsOf(taskId).isEmpty());
    }

    // ==================================================================
    // 覆盖6: 时长 2 连续大节
    // ==================================================================

    @Test
    @DisplayName("覆盖6: durationSlots=2 的实验课占用同一日相邻两节")
    void durationTwo_labUsesTwoConsecutivePeriods() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "L", Constants.COURSE_LAB, Constants.ROOM_LAB);
        long room = saveClassroom(s + "L", 200, Constants.ROOM_LAB);
        long offering = saveOffering(course, teacherA, s + "1", 1, 2);
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), 99L, null);

        JsonNode data = runOk(taskId);
        assertEquals("FEASIBLE", data.path("outcome").asText());
        assertEquals(0, data.path("hardViolation").asInt());

        List<ScheduleEntry> entries = entriesOf(taskId);
        assertEquals(1, entries.size());
        ScheduleEntry entry = entries.get(0);
        // 落库只存起始时间段; 校验其与相邻下一节构成连续占用(不落第二行)
        TimeSlot start = timeSlots.stream()
                .filter(ts -> ts.getId().equals(entry.getTimeSlotId()))
                .findFirst().orElseThrow();
        assertNotNull(slotIds.get(start.getDayOfWeek() + ":" + (start.getPeriod() + 1)),
                "时长2课程必须从可续接的时间段开始");
        assertTrue(start.getPeriod() + 1 <= 5);
    }

    // ==================================================================
    // 覆盖7: 合班多班级
    // ==================================================================

    @Test
    @DisplayName("覆盖7: 合班 offering 一个条目覆盖全部授课班级")
    void multiClassOffering_combinesAllClasses() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long k2 = saveClazz(s + "K2", 40);
        long k3 = saveClazz(s + "K3", 50);
        long course = saveCourse(s + "MU", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1, k2, k3); // 三班合上
        long taskId = saveTask(s, List.of(offering), List.of(room), 8L, null);

        JsonNode data = runOk(taskId);
        assertEquals("FEASIBLE", data.path("outcome").asText());

        List<ScheduleEntry> entries = entriesOf(taskId);
        assertEquals(1, entries.size());
        Map<Long, Set<Long>> classesByEntry = groupClassesByEntry(
                entryClassRepository.findByScheduleEntryIdInOrderByIdAsc(
                        entries.stream().map(ScheduleEntry::getId).toList()));
        assertEquals(Set.of(k1, k2, k3), classesByEntry.get(entries.get(0).getId()),
                "合班条目必须写全 3 个班级");
    }

    // ==================================================================
    // 覆盖8: 教师/班级/教室三类资源零重叠
    // ==================================================================

    @Test
    @DisplayName("覆盖8: 排课结果教师/班级/教室任一资源同一时间不重叠")
    void schedule_hasNoResourceOverlaps() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long userB = saveUser(s + "_b", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long teacherB = saveTeacher(userB, s + "B");
        long k1 = saveClazz(s + "K1", 30);
        long k2 = saveClazz(s + "K2", 40);
        long cA = saveCourse(s + "A", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long cB = saveCourse(s + "B", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room1 = saveClassroom(s + "N1", 200, Constants.ROOM_NORMAL);
        long room2 = saveClassroom(s + "N2", 200, Constants.ROOM_NORMAL);
        // 教师A: o1(k1), o2(k2) 各每周2次; 教师B: o3(k2) 每周2次 → 共6个单元
        long o1 = saveOffering(cA, teacherA, s + "1", 2, 1);
        long o2 = saveOffering(cA, teacherA, s + "2", 2, 1);
        long o3 = saveOffering(cB, teacherB, s + "3", 2, 1);
        offeringClasses(o1, k1);
        offeringClasses(o2, k2);
        offeringClasses(o3, k2);
        long taskId = saveTask(s, List.of(o1, o2, o3), List.of(room1, room2), 123L, null);

        JsonNode data = runOk(taskId);
        assertEquals("FEASIBLE", data.path("outcome").asText());
        assertEquals(0, data.path("hardViolation").asInt());
        assertEquals(6, entriesOf(taskId).size());
        assertNoOverlapsAndContinuous(taskId);
    }

    // ==================================================================
    // 覆盖12: 教师偏好软约束
    // ==================================================================

    @Test
    @DisplayName("覆盖12: 教师全部偏好为不偏好(-1)时能量增加 w1×占用数(100)")
    void teacherPreference_influencesSoftPenalty() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherPref = saveTeacher(userA, s + "P");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);

        // 班级不可用: 仅剩 (周三第3节, 周五第3节) 两个候选 → 2 个 session 必须用这两节, 解唯一
        List<Long> all = allSlotIds();
        Set<Long> allowed = Set.of(slot(3, 3), slot(5, 3));
        List<Long> blockedClass = all.stream().filter(id -> !allowed.contains(id)).toList();
        blockSlots(Constants.RESOURCE_CLASS, k1, blockedClass, "只允许周三/周五第三节上课");
        long offering = saveOffering(course, teacherPref, s + "1", 2, 1);
        offeringClasses(offering, k1);
        long taskPlain = saveTask(s + "P", List.of(offering), List.of(room), 2026L, null);

        // 基线: 无任何偏好记录 → S1 = 0
        runOk(taskPlain);
        double plainEnergy = resultRepository.findByTaskId(taskPlain).orElseThrow().getBestFitness();

        // 对照组: 同一开课/教师/候选集, 仅补上"全部时段不偏好" → S1 = 2
        savePreference(teacherPref, -1, all);
        long taskPrefDisliked = saveTask(s + "D", List.of(offering), List.of(room), 2026L, null);
        runOk(taskPrefDisliked);
        double dislikedEnergy =
                resultRepository.findByTaskId(taskPrefDisliked).orElseThrow().getBestFitness();

        // 两个 session 各落在不偏好段 → S1 原始违反 = 2, 能量差 = w1×2 = 100
        assertEquals(100.0, dislikedEnergy - plainEnergy, 1e-6,
                "不偏好软约束应按 w1×S1 计入能量: plain=" + plainEnergy + ", disliked=" + dislikedEnergy);
    }

    // ==================================================================
    // 覆盖13/14/15: 三类资源不可用过滤
    // ==================================================================

    @Test
    @DisplayName("覆盖9(教师不可用过滤): 只剩 1 个候选时排课落在该时间段")
    void teacherUnavailability_pinsSingleCandidate() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);

        long onlyFree = slot(4, 2); // 周四第2节唯一可用
        List<Long> blocked = allSlotIds().stream().filter(id -> id != onlyFree).toList();
        blockSlots(Constants.RESOURCE_TEACHER, teacherA, blocked, "只留周四第2节");
        long taskId = saveTask(s, List.of(offering), List.of(room), 5L, null);

        JsonNode data = runOk(taskId);
        assertEquals("FEASIBLE", data.path("outcome").asText());
        assertEquals(0, data.path("hardViolation").asInt());

        List<ScheduleEntry> entries = entriesOf(taskId);
        assertEquals(1, entries.size());
        assertEquals(onlyFree, entries.get(0).getTimeSlotId(),
                "教师不可用过滤后应唯一落在剩余时间段");
    }

    @Test
    @DisplayName("覆盖9(班级不可用过滤): 排课不会占用班级不可用时间段")
    void classUnavailability_excludesBlockedPeriods() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 2, 1);
        offeringClasses(offering, k1);

        // 周二全天 + 周四全部不可用(不影响可行性: 仍剩 3 天可用)
        List<Long> blocked = new ArrayList<>();
        for (int day : new int[]{2, 4}) {
            for (int p = 1; p <= 5; p++) {
                blocked.add(slot(day, p));
            }
        }
        blockSlots(Constants.RESOURCE_CLASS, k1, blocked, "班级周二/周四活动");
        long taskId = saveTask(s, List.of(offering), List.of(room), 3L, null);

        JsonNode data = runOk(taskId);
        assertEquals("FEASIBLE", data.path("outcome").asText());
        assertEquals(0, data.path("hardViolation").asInt());

        Map<Long, TimeSlot> slotById = new HashMap<>();
        for (TimeSlot ts : timeSlots) {
            slotById.put(ts.getId(), ts);
        }
        for (ScheduleEntry entry : entriesOf(taskId)) {
            int day = slotById.get(entry.getTimeSlotId()).getDayOfWeek();
            assertTrue(day != 2 && day != 4, "条目不应落在班级不可用的周二/周四");
        }
    }

    @Test
    @DisplayName("覆盖9(教室不可用过滤): 被屏蔽教室不会被排课使用")
    void classroomUnavailability_filtersCandidates() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long roomBad = saveClassroom(s + "BAD", 200, Constants.ROOM_NORMAL);
        long roomGood = saveClassroom(s + "GOOD", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);

        // 全部时段屏蔽 BAD 教室: 只剩 GOOD 教室的候选
        blockSlots(Constants.RESOURCE_CLASSROOM, roomBad, allSlotIds(), "该教室停用");
        long taskId = saveTask(s, List.of(offering), List.of(roomBad, roomGood), 11L, null);

        JsonNode data = runOk(taskId);
        assertEquals("FEASIBLE", data.path("outcome").asText());
        assertEquals(1, entriesOf(taskId).size());
        assertEquals(roomGood, entriesOf(taskId).get(0).getClassroomId(),
                "教室不可用过滤后只允许使用 GOOD 教室");
    }

    // ==================================================================
    // 覆盖3/4/17: 状态防重入与幂等
    // ==================================================================

    @Test
    @DisplayName("附加: COMPLETED 任务重复执行被拒绝(结果唯一性守护)")
    void rerunCompletedTask_rejectedWithoutDuplicates() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), 13L, null);

        JsonNode first = runOk(taskId);
        long firstResultId = first.path("resultId").asLong();

        JsonNode second = runExpectCode(taskId, adminToken(), 200, 400);
        assertTrue(second.path("message").asText().contains("仅 PENDING"), "实际: " + second);

        assertEquals(Constants.TASK_COMPLETED, task(taskId).getStatus());
        assertEquals(1, resultsOf(taskId).size(), "重复执行不得产生第二份结果");
        assertEquals(firstResultId, resultsOf(taskId).get(0).getId());
        assertFalse(entriesOf(taskId).isEmpty());
    }

    @Test
    @DisplayName("附加: RUNNING 状态再次认领被拒绝(并发防双跑)")
    void claimRunningTwice_rejected() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), 17L, null);

        SchedulingTask running = store.claimRunning(taskId);
        assertEquals(Constants.TASK_RUNNING, running.getStatus());

        BusinessException ex = assertThrows(BusinessException.class, () -> store.claimRunning(taskId));
        assertTrue(ex.getMessage().contains("仅 PENDING"), "实际: " + ex.getMessage());
        assertEquals(Constants.TASK_RUNNING, task(taskId).getStatus());
        assertTrue(resultsOf(taskId).isEmpty());
    }

    // ==================================================================
    // 覆盖17: RBAC(ADMIN 通过 / TEACHER 403 / 无 Token 401)
    // ==================================================================

    @Test
    @DisplayName("覆盖13/14: run 接口 RBAC - ADMIN 可执行, TEACHER 403, 无 Token 401")
    void rbac_adminAllowed_teacherForbidden_noToken401() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);
        long taskId = saveTask(s, List.of(offering), List.of(room), 23L, null);

        // 无 Token → 401
        runExpectCode(taskId, null, 401, 401);
        assertEquals(Constants.TASK_PENDING, task(taskId).getStatus(), "未授权请求不应改变状态");

        // TEACHER → 403
        runExpectCode(taskId, teacherToken(), 403, 403);
        assertEquals(Constants.TASK_PENDING, task(taskId).getStatus(), "无权限请求不应改变状态");

        // ADMIN → 成功
        JsonNode data = runOk(taskId);
        assertEquals(Constants.TASK_COMPLETED, data.path("status").asText());
    }

    // ==================================================================
    // 覆盖18: 算法异常 → FAILED
    // ==================================================================

    @Test
    @DisplayName("覆盖15: SA 参数非法导致算法异常时任务 FAILED 且无结果")
    void algorithmException_marksTaskFailed() throws Exception {
        String s = uniqueSuffix();
        long userA = saveUser(s + "_a", Constants.ROLE_TEACHER);
        long teacherA = saveTeacher(userA, s + "A");
        long k1 = saveClazz(s + "K1", 30);
        long course = saveCourse(s + "T", Constants.COURSE_THEORY, Constants.ROOM_NORMAL);
        long room = saveClassroom(s + "N", 200, Constants.ROOM_NORMAL);
        long offering = saveOffering(course, teacherA, s + "1", 1, 1);
        offeringClasses(offering, k1);
        // coolingRate=0 违反 (0,1) 校验 → SimulatedAnnealingParams 构造抛异常
        long taskId = saveTask(s, List.of(offering), List.of(room), null, 0.0);

        JsonNode body = runExpectCode(taskId, adminToken(), 500, 500);
        assertTrue(body.path("message").asText().contains("系统内部错误"),
                "应返回系统错误, 实际: " + body);

        assertEquals(Constants.TASK_FAILED, task(taskId).getStatus(), "系统异常必须落 FAILED");
        assertNotNull(task(taskId).getFinishTime());
        assertTrue(resultsOf(taskId).isEmpty());
        assertTrue(entriesOf(taskId).isEmpty());
    }
}
