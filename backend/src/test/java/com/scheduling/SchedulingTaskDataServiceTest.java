package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.CourseOfferingClass;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.SchedulingTaskClassroom;
import com.scheduling.entity.SchedulingTaskCourse;
import com.scheduling.entity.Teacher;
import com.scheduling.entity.User;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ClassroomRepository;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingClassRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.SchedulingTaskClassroomRepository;
import com.scheduling.repository.SchedulingTaskCourseRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.repository.TimeSlotRepository;
import com.scheduling.repository.UserRepository;
import com.scheduling.service.SchedulingTaskService;
import com.scheduling.vo.CourseOfferingData;
import com.scheduling.vo.SchedulingTaskData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排课任务完整数据装配服务测试 (第3步第三阶段 3C-4)
 *
 * 覆盖: SchedulingTaskService.getSchedulingTaskData 从单个任务装配
 *   task + 纳入 offerings(含 course/teacher 冗余与班级集合) + 教室池 + 全量时间段,
 *   顺序稳定 / 任务不存在404 / 范围引用悬挂(offering 缺失) 拒绝装配 400。
 * 本阶段不构造任何算法结构(SchedulingUnit 等留后续阶段)。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchedulingTaskDataServiceTest extends BaseCrudApiTest {

    @Autowired
    private SchedulingTaskService schedulingTaskService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private ClazzRepository clazzRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private CourseOfferingRepository courseOfferingRepository;
    @Autowired
    private CourseOfferingClassRepository courseOfferingClassRepository;
    @Autowired
    private SchedulingTaskRepository schedulingTaskRepository;
    @Autowired
    private SchedulingTaskCourseRepository schedulingTaskCourseRepository;
    @Autowired
    private SchedulingTaskClassroomRepository schedulingTaskClassroomRepository;
    @Autowired
    private TimeSlotRepository timeSlotRepository;

    private static class Fixture {
        long userId;
        long courseId;
        long teacherId;
        final List<Long> classIds = new ArrayList<>();
        final List<Long> classroomIds = new ArrayList<>();
        final List<Long> offeringIds = new ArrayList<>();
        final List<Long> taskIds = new ArrayList<>();
        long taskId;
    }

    private String tag() {
        return String.valueOf(System.nanoTime()).substring(6);
    }

    private User createUser() {
        User user = User.builder()
                .username("u" + tag())
                .password("p")
                .realName("测试")
                .role(Constants.ROLE_TEACHER)
                .status(1)
                .build();
        return userRepository.save(user);
    }

    private Fixture createFixture() {
        Fixture fx = new Fixture();
        fx.userId = createUser().getId();
        Course course = courseRepository.save(Course.builder()
                .courseCode("CO" + tag()).courseName("数据结构")
                .courseType(Constants.COURSE_THEORY).requiredRoomType(Constants.ROOM_NORMAL).build());
        fx.courseId = course.getId();
        Teacher teacher = teacherRepository.save(Teacher.builder()
                .userId(fx.userId).teacherNo("T" + tag()).name("陈明").build());
        fx.teacherId = teacher.getId();
        return fx;
    }

    private long createClass(Fixture fx, String name) {
        Clazz clazz = clazzRepository.save(Clazz.builder()
                .className(name == null ? "CLS" + tag() : name)
                .studentCount(40).build());
        fx.classIds.add(clazz.getId());
        return clazz.getId();
    }

    private long createClassroom(Fixture fx) {
        Classroom room = classroomRepository.save(Classroom.builder()
                .roomNo("RM" + tag()).capacity(80).roomType(Constants.ROOM_NORMAL).build());
        fx.classroomIds.add(room.getId());
        return room.getId();
    }

    private long createOffering(Fixture fx, String semester, Long... classIds) {
        CourseOffering offering = courseOfferingRepository.save(CourseOffering.builder()
                .courseId(fx.courseId).teacherId(fx.teacherId)
                .semester(semester).weeklySessions(2).durationSlots(1).isLabCourse(0).build());
        fx.offeringIds.add(offering.getId());
        for (Long classId : classIds) {
            courseOfferingClassRepository.save(CourseOfferingClass.builder()
                    .courseOfferingId(offering.getId()).classId(classId).build());
        }
        return offering.getId();
    }

    private long createTask(Fixture fx, String semester, List<Long> offeringIds, List<Long> classroomIds) {
        SchedulingTask task = schedulingTaskRepository.save(SchedulingTask.builder()
                .taskName("装配任务").semester(semester)
                .status(Constants.TASK_PENDING)
                .createTime(LocalDateTime.now())
                .build());
        fx.taskIds.add(task.getId());
        fx.taskId = task.getId();
        for (Long offeringId : offeringIds) {
            schedulingTaskCourseRepository.save(SchedulingTaskCourse.builder()
                    .schedulingTaskId(task.getId()).courseOfferingId(offeringId).build());
        }
        for (Long classroomId : classroomIds) {
            schedulingTaskClassroomRepository.save(SchedulingTaskClassroom.builder()
                    .schedulingTaskId(task.getId()).classroomId(classroomId).build());
        }
        return task.getId();
    }

    private void cleanup(Fixture fx) {
        for (long taskId : fx.taskIds) {
            try {
                schedulingTaskClassroomRepository.deleteBySchedulingTaskId(taskId);
                schedulingTaskCourseRepository.deleteBySchedulingTaskId(taskId);
                schedulingTaskRepository.deleteById(taskId);
            } catch (RuntimeException ignored) {
            }
        }
        for (long offeringId : fx.offeringIds) {
            try {
                courseOfferingClassRepository.deleteByCourseOfferingId(offeringId);
                courseOfferingRepository.deleteById(offeringId);
            } catch (RuntimeException ignored) {
            }
        }
        for (long classroomId : fx.classroomIds) {
            try {
                classroomRepository.deleteById(classroomId);
            } catch (RuntimeException ignored) {
            }
        }
        for (long classId : fx.classIds) {
            try {
                clazzRepository.deleteById(classId);
            } catch (RuntimeException ignored) {
            }
        }
        if (fx.teacherId != 0) {
            try {
                teacherRepository.deleteById(fx.teacherId);
            } catch (RuntimeException ignored) {
            }
        }
        if (fx.courseId != 0) {
            try {
                courseRepository.deleteById(fx.courseId);
            } catch (RuntimeException ignored) {
            }
        }
        if (fx.userId != 0) {
            try {
                userRepository.deleteById(fx.userId);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Test
    @Transactional
    @DisplayName("3C4装配1: 完整装配含 task/offerings/每个offering的classes/教室池/全量时间段且顺序稳定")
    void assembleFullTaskData() {
        Fixture fx = createFixture();
        String semester = "2026秋" + tag();
        try {
            long c1 = createClass(fx, null);
            long c2 = createClass(fx, null);
            long r1 = createClassroom(fx);
            long r2 = createClassroom(fx);
            // offering1 合班(c1,c2), offering2 单班(c1), offering3 无班级(独立覆盖场景)
            long o1 = createOffering(fx, semester, c1, c2);
            long o2 = createOffering(fx, semester, c1);
            long o3 = createOffering(fx, semester);
            long taskId = createTask(fx, semester, List.of(o2, o1, o3), List.of(r2, r1));

            SchedulingTaskData data = schedulingTaskService.getSchedulingTaskData(taskId);

            // ---- 任务本体 ----
            assertNotNull(data.getTask());
            assertEquals(taskId, data.getTask().getId());
            assertEquals(semester, data.getTask().getSemester());
            assertEquals(Constants.TASK_PENDING, data.getTask().getStatus());
            assertNull(data.getTask().getHardConstraintWeight(), "W_hard 仍应保持 null");

            // ---- offerings 按任务纳入顺序(o2, o1, o3), 而非 id 顺序 ----
            assertEquals(3, data.getOfferings().size());
            assertEquals(o2, data.getOfferings().get(0).getOffering().getId());
            assertEquals(o1, data.getOfferings().get(1).getOffering().getId());
            assertEquals(o3, data.getOfferings().get(2).getOffering().getId());

            // ---- 每个 offering 的 course/teacher 冗余与班级 ----
            Map<Long, CourseOfferingData> byId = data.getOfferings().stream()
                    .collect(Collectors.toMap(d -> d.getOffering().getId(), Function.identity()));
            assertNotNull(byId.get(o1).getCourse(), "offering 应冗余带出课程");
            assertEquals(fx.courseId, byId.get(o1).getCourse().getId());
            assertNotNull(byId.get(o1).getTeacher(), "offering 应冗余带出教师");
            assertEquals(fx.teacherId, byId.get(o1).getTeacher().getId());
            assertEquals(2, byId.get(o1).getClasses().size(), "合班 offering 应有2个班级");
            assertEquals(c1, byId.get(o1).getClasses().get(0).getId());
            assertEquals(c2, byId.get(o1).getClasses().get(1).getId());
            assertEquals(1, byId.get(o2).getClasses().size());
            assertEquals(c1, byId.get(o2).getClasses().get(0).getId());
            assertTrue(byId.get(o3).getClasses().isEmpty(), "无班级的 offering 班级列表应为空");

            // ---- 教室池按纳入顺序(r2, r1) ----
            assertEquals(2, data.getClassrooms().size());
            assertEquals(r2, data.getClassrooms().get(0).getId());
            assertEquals(r1, data.getClassrooms().get(1).getId());

            // ---- 全量时间段 = 5天×5大节 = 25 ----
            assertEquals(25, data.getTimeSlots().size());
            int prev = 0;
            for (int i = 0; i < data.getTimeSlots().size(); i++) {
                var slot = data.getTimeSlots().get(i);
                int key = slot.getDayOfWeek() * 10 + slot.getPeriod();
                assertTrue(key > prev, "时间段应按(dayOfWeek,period)升序");
                prev = key;
            }
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @Transactional
    @DisplayName("3C4装配2: 任务不存在 -> code404")
    void taskNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> schedulingTaskService.getSchedulingTaskData(99999999L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @Transactional
    @DisplayName("3C4装配3: 范围引用悬挂(offering 被绕过保护直删) -> 拒绝装配 code400")
    void danglingOfferingRejected() {
        Fixture fx = createFixture();
        String semester = "2026秋" + tag();
        try {
            long c1 = createClass(fx, null);
            long o1 = createOffering(fx, semester, c1);
            long taskId = createTask(fx, semester, List.of(o1), List.of());

            // 模拟数据损坏: 绕过服务层删除保护, 直接删 offering 及其 class 关联
            courseOfferingClassRepository.deleteByCourseOfferingId(o1);
            courseOfferingRepository.deleteById(o1);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> schedulingTaskService.getSchedulingTaskData(taskId));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("开课实例不存在"));
        } finally {
            cleanup(fx);
        }
    }

    @Test
    @Transactional
    @DisplayName("3C4装配4: 空范围任务可装配(offerings/classrooms 为空, timeSlots 仍全量)")
    void emptyScopeAssemble() {
        Fixture fx = createFixture();
        String semester = "2026春" + tag();
        try {
            long taskId = createTask(fx, semester, List.of(), List.of());
            SchedulingTaskData data = schedulingTaskService.getSchedulingTaskData(taskId);
            assertNotNull(data.getTask());
            assertTrue(data.getOfferings().isEmpty());
            assertTrue(data.getClassrooms().isEmpty());
            assertEquals(25, data.getTimeSlots().size());
        } finally {
            cleanup(fx);
        }
    }
}
