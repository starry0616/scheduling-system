package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.dto.SchedulingRunResponse;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.Teacher;
import com.scheduling.execution.SchedulingExecutionService;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.ClassroomRepository;
import com.scheduling.repository.CourseOfferingClassRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.SchedulingResultRepository;
import com.scheduling.repository.SchedulingTaskCourseRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.service.DemoDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demo 数据初始化集成测试 (8.3-S 第一批 C2)
 *
 * <p>验证目标:
 * <ol>
 *   <li><b>幂等性</b>: 连续执行两次初始化 —— 第二次必须整体跳过(返回 false),
 *       不产生重复数据、不产生唯一键冲突、不破坏已有数据;</li>
 *   <li><b>冒烟</b>: Demo 数据能被 SchedulingProblemAssembler 正确装配,
 *       演示任务(PENDING)实际执行一次小规模排课并得到 FEASIBLE / hardViolation=0。</li>
 * </ol>
 *
 * <p>全部用例运行在事务内, 结束时整体回滚 —— 不会向共享测试库写入任何演示数据,
 * 也不会把演示任务改写成 COMPLETED(答辩现场仍保有可点击执行的 PENDING 任务)。
 * 若共享库已存在演示数据(例如曾在开发库手工/真实启动后端), 用例自动走"已初始化"断言路径。
 */
@SpringBootTest
@ActiveProfiles("test")
class DemoDataIntegrationTest {

    @Autowired private DemoDataService demoDataService;
    @Autowired private SchedulingExecutionService schedulingExecutionService;

    @Autowired private CourseRepository courseRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private ClazzRepository clazzRepository;
    @Autowired private ClassroomRepository classroomRepository;
    @Autowired private CourseOfferingRepository courseOfferingRepository;
    @Autowired private CourseOfferingClassRepository courseOfferingClassRepository;
    @Autowired private SchedulingTaskRepository schedulingTaskRepository;
    @Autowired private SchedulingTaskCourseRepository schedulingTaskCourseRepository;
    @Autowired private SchedulingResultRepository schedulingResultRepository;

    private static final Set<String> DEMO_COURSE_CODES = Set.of(
            "CS2201", "CS3202", "SE4301", "DB3103", "OS3304", "CS2250", "CS3251", "SE4350");

    // ---------- 工具 ----------

    /** 本库中演示课程的全部 id */
    private Set<Long> demoCourseIds() {
        Set<Long> ids = new HashSet<>();
        for (Course course : courseRepository.findAll()) {
            if (DEMO_COURSE_CODES.contains(course.getCourseCode())) {
                ids.add(course.getId());
            }
        }
        return ids;
    }

    private long countDemoOfferings() {
        Set<Long> courseIds = demoCourseIds();
        return courseOfferingRepository.findAll().stream()
                .filter(o -> courseIds.contains(o.getCourseId()))
                .filter(o -> DemoDataService.DEMO_SEMESTER.equals(o.getSemester()))
                .count();
    }

    private long countByName(List<String> values, String target) {
        return values.stream().filter(target::equals).count();
    }

    private long countTasks() {
        return schedulingTaskRepository.findAll().stream()
                .filter(t -> DemoDataService.DEMO_TASK_NAME.equals(t.getTaskName()))
                .count();
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("C2-1: 连续执行两次初始化, 第二次必须整体跳过且不产生重复数据/唯一键冲突")
    @Transactional
    void initializeTwiceIsIdempotent() {
        boolean first = demoDataService.initializeIfNeeded();
        if (first) {
            // 本事务真正执行了初始化: 基础数据规模应符合 Demo 定义
            assertEquals(8, demoCourseIds().size(), "演示课程应为 8 门");
            assertEquals(17, countDemoOfferings(), "演示开课实例应为 17 条");
            assertEquals(1, countTasks(), "演示任务应恰好创建 1 个");
        }

        // 第二次必须幂等整体跳过(不允许重复插入 / 不允许任何唯一键冲突)
        boolean second = demoDataService.initializeIfNeeded();
        assertFalse(second, "第二次初始化必须整体跳过(不允许重复插入)");
        long offerings = countDemoOfferings();
        long tasks = countTasks();

        // 第三次同样必须跳过且不改变任何数据
        assertFalse(demoDataService.initializeIfNeeded());
        assertEquals(offerings, countDemoOfferings(), "重复执行不应改变开课实例数量");
        assertEquals(tasks, countTasks(), "重复执行不应改变演示任务数量");

        // 结构完整性: 演示任务存在且为 PENDING(答辩现场可直接点击开始排课)
        SchedulingTask demoTask = schedulingTaskRepository.findAll().stream()
                .filter(t -> DemoDataService.DEMO_TASK_NAME.equals(t.getTaskName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应存在演示任务"));
        assertEquals(DemoDataService.DEMO_SEMESTER, demoTask.getSemester());
        assertEquals(Constants.TASK_PENDING, demoTask.getStatus(),
                "答辩演示任务应保持 PENDING, 以便现场点击开始排课");
        // 范围关联完整: 任务纳入的开课实例数 = 全部演示开课实例数
        long linkedOfferings = schedulingTaskCourseRepository.findAll().stream()
                .filter(c -> c.getSchedulingTaskId().equals(demoTask.getId()))
                .count();
        assertEquals(countDemoOfferings(), linkedOfferings, "任务范围应覆盖全部演示开课实例");
    }

    @Test
    @DisplayName("C2-2: Demo 基础数据规模与引用完整性校验")
    @Transactional
    void demoBaseDataScaleAndReferenceIntegrity() {
        demoDataService.initializeIfNeeded();

        assertEquals(8, demoCourseIds().size(), "演示课程 8 门");
        assertEquals(6, countDemoBy(teacherRepository.findAll().stream()
                .map(Teacher::getTeacherNo).toList(), "T202600", true),
                "演示教师 6 人");
        assertEquals(7, countDemoClasses(), "演示班级 7 个");
        assertEquals(10, countDemoRooms(), "演示教室 10 间");
        assertEquals(17, countDemoOfferings(), "演示开课实例 17 条");

        // 引用完整性: 每条演示开课实例都能解析到 课程/教师/班级关联
        Set<Long> courseIds = demoCourseIds();
        Set<Long> demoTeacherIds = new HashSet<>();
        teacherRepository.findAll().forEach(t -> {
            if (t.getTeacherNo().startsWith("T2026")) {
                demoTeacherIds.add(t.getId());
            }
        });
        Set<Long> demoClassIds = new HashSet<>();
        List<String> classNames = clazzRepository.findAll().stream().map(Clazz::getClassName).toList();
        for (String cn : classNames) {
            if (cn.startsWith("软件工程240") || cn.startsWith("计算机科学与技术240")
                    || cn.startsWith("数据科学与大数据技术2401") || cn.startsWith("网络工程2401")) {
                clazzRepository.findAll().stream()
                        .filter(c -> c.getClassName().equals(cn)).findFirst()
                        .ifPresent(c -> demoClassIds.add(c.getId()));
            }
        }
        for (CourseOffering o : courseOfferingRepository.findAll()) {
            if (!courseIds.contains(o.getCourseId())
                    || !DemoDataService.DEMO_SEMESTER.equals(o.getSemester())) {
                continue;
            }
            assertTrue(demoTeacherIds.contains(o.getTeacherId()),
                    "开课实例 teacher 引用必须完整: offeringId=" + o.getId());
        }
        // 每条演示开课实例至少有 1 条班级关联(装配器依赖 classIds 非空)
        List<Long> offeringIds = courseOfferingRepository.findAll().stream()
                .filter(o -> courseIds.contains(o.getCourseId()))
                .filter(o -> DemoDataService.DEMO_SEMESTER.equals(o.getSemester()))
                .map(CourseOffering::getId)
                .toList();
        assertTrue(offeringIds.size() == 17);
        long linkedClassRows = courseOfferingClassRepository.findAll().stream()
                .filter(oc -> offeringIds.contains(oc.getCourseOfferingId()))
                .count();
        assertEquals(17, linkedClassRows, "每个演示开课实例应关联且仅关联 1 个班级");
        assertEquals(demoClassIds.size(), countDemoClasses(), "演示班级引用齐全");
    }

    @Test
    @DisplayName("C2-3: 演示任务可被 SchedulingProblemAssembler 装配并实际执行(小规模排课冒烟)")
    @Transactional
    void demoPendingTaskCanRunFeasible() {
        demoDataService.initializeIfNeeded();

        List<SchedulingTask> tasks = schedulingTaskRepository.findAll().stream()
                .filter(t -> DemoDataService.DEMO_TASK_NAME.equals(t.getTaskName()))
                .toList();
        assertFalse(tasks.isEmpty(), "演示任务应存在");

        SchedulingTask task = tasks.stream()
                .filter(t -> Constants.TASK_PENDING.equals(t.getStatus()))
                .findFirst()
                .orElse(tasks.get(0));

        if (Constants.TASK_PENDING.equals(task.getStatus())) {
            // 真实执行一次小规模排课(默认 SA 参数): 必须 FEASIBLE 且 0 硬冲突
            SchedulingRunResponse response = schedulingExecutionService.run(task.getId());
            assertEquals(Constants.TASK_COMPLETED, response.getStatus());
            assertEquals("FEASIBLE", response.getOutcome());
            assertTrue(response.isFeasible());
            assertEquals(0, response.getHardViolation());
            assertNotNull(response.getHardConstraintWeight());
            assertTrue(response.getHardConstraintWeight() > 0, "应回填 W_hard");

            SchedulingTask after = schedulingTaskRepository.findById(task.getId()).orElseThrow();
            assertEquals(Constants.TASK_COMPLETED, after.getStatus(), "任务应迁移为 COMPLETED");
            assertNotNull(after.getHardConstraintWeight(), "任务应回填硬约束权重");
            // 结果行落库
            assertTrue(schedulingResultRepository.findByTaskId(task.getId()).isPresent(),
                    "排课结果应已落库");
        } else {
            // 兼容: 开发库中演示任务已被真实执行过(COMPLETED), 直接校验既有结果的完整性
            assertEquals(Constants.TASK_COMPLETED, task.getStatus(),
                    "演示任务若已非 PENDING, 应为 COMPLETED(由本测试事务保证不会污染状态)");
            assertNotNull(task.getHardConstraintWeight());
            assertTrue(task.getHardConstraintWeight() > 0, "已完成演示任务应回填 W_hard");
            assertTrue(schedulingResultRepository.findByTaskId(task.getId()).isPresent(),
                    "已完成演示任务应存在结果记录");
        }
    }

    // ---------- 辅助计数 ----------

    private long countDemoClasses() {
        return clazzRepository.findAll().stream()
                .filter(c -> c.getClassName().startsWith("软件工程240")
                        || c.getClassName().startsWith("计算机科学与技术240")
                        || c.getClassName().startsWith("数据科学与大数据技术2401")
                        || c.getClassName().startsWith("网络工程2401"))
                .count();
    }

    private long countDemoRooms() {
        return classroomRepository.findAll().stream()
                .filter(r -> r.getRoomNo().matches("[NML]\\d{2,3}"))
                .count();
    }

    private long countDemoBy(List<String> values, String prefix, boolean startsWith) {
        if (startsWith) {
            return values.stream().filter(v -> v.startsWith(prefix)).count();
        }
        return countByName(values, prefix);
    }
}
