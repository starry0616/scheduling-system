package com.scheduling.service;

import com.scheduling.common.Constants;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.CourseOfferingClass;
import com.scheduling.entity.ResourceUnavailability;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.SchedulingTaskClassroom;
import com.scheduling.entity.SchedulingTaskCourse;
import com.scheduling.entity.Teacher;
import com.scheduling.entity.TeacherPreference;
import com.scheduling.entity.TimeSlot;
import com.scheduling.entity.User;
import com.scheduling.repository.ClassroomRepository;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingClassRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.ResourceUnavailabilityRepository;
import com.scheduling.repository.SchedulingTaskClassroomRepository;
import com.scheduling.repository.SchedulingTaskCourseRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import com.scheduling.repository.TeacherPreferenceRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.repository.TimeSlotRepository;
import com.scheduling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示(Demo)数据初始化服务 (8.3-S 第一批 C2)
 *
 * <p>为毕业答辩准备一套<b>稳定、可重复、可直接运行</b>的演示数据, 覆盖:
 * 账号(admin/教师)、课程、教师、班级、教室(三种类型)、开课实例(理论/多媒体/LAB,
 * weeklySessions 1 与 2)、教师偏好(S1 演示)、资源不可用(少量且不破坏可行性)、
 * 以及一个可直接现场执行排课的 PENDING 演示任务。
 *
 * <p>设计原则(初始化安全):
 * <ul>
 *   <li><b>幂等</b>: 以"course 表不存在演示标记课程"为整体判据 —— 只在<b>从未初始化过</b>
 *       演示数据的库中执行; 已存在则整体跳过, 第二次启动绝不产生重复数据 / 唯一键冲突;</li>
 *   <li><b>不破坏</b>: 只插入、不更新、不删除、不覆盖任何已有数据; 不使用随机值/时间随机
 *       数据, 全部键(工号/课程代码/班级名/教室编号/账号)固定可复现;</li>
 *   <li><b>原子性</b>: 整个初始化在<b>一个事务</b>内完成, 任一步失败整体回滚,
 *       绝不残留"插了一半"的脏数据;</li>
 *   <li><b>与运行模式解耦</b>: 由 {@link com.scheduling.config.DemoDataInitializer}
 *       (ApplicationRunner, application.yml 的 app.demo-data.enabled=true) 在应用启动时调用;
 *       本服务不持有任何顺序依赖(time_slot 缺失时按标准 25 时段自补全)。</li>
 * </ul>
 *
 * <p>说明: 本服务直接使用 JPA Repository 写入, 不再经 Controller/Service 校验层 ——
 * 因为数据规模与引用关系在下方常量中已显式保证一致(参考 SchedulingProblemAssembler 的装配要求);
 * 演示任务通过 repository 建立 scheduling_task 及其两张范围关联表, 与调度执行层读取契约一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataService {

    /** 演示学期(任务与开课实例统一使用, 供答辩现场检索) */
    public static final String DEMO_SEMESTER = "2026秋";

    /** 演示任务名称(答辩现场可直接在任务列表看到并点击"开始排课") */
    public static final String DEMO_TASK_NAME = "2026秋 软件学院演示排课任务";

    /** 演示数据初始化判据: 只要 course 表存在该代码, 即认为演示数据已初始化, 整体跳过 */
    public static final String MARKER_COURSE_CODE = "CS2201";

    private static final String DEMO_ACCOUNT_PASSWORD = "teacher123";

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    // ---------- 演示数据常量(固定键, 可复现) ----------

    /** 演示教师: [工号, 姓名, 职称, 院系, 登录账号] */
    private static final String[][] DEMO_TEACHERS = {
            {"T2026001", "陈明理", "教授", "软件学院", "demo_teacher01"},
            {"T2026002", "李慧敏", "副教授", "软件学院", "demo_teacher02"},
            {"T2026003", "刘雅琴", "讲师", "软件学院", "demo_teacher03"},
            {"T2026004", "张晓东", "副教授", "计算机学院", "demo_teacher04"},
            {"T2026005", "周启航", "教授", "计算机学院", "demo_teacher05"},
            {"T2026006", "王建斌", "讲师", "软件学院", "demo_teacher06"},
    };

    /** 演示班级: [班级名, 年级, 人数] */
    private static final String[][] DEMO_CLASSES = {
            {"软件工程2401", "2024级", "40"},
            {"软件工程2402", "2024级", "40"},
            {"软件工程2403", "2024级", "40"},
            {"计算机科学与技术2401", "2024级", "42"},
            {"计算机科学与技术2402", "2024级", "42"},
            {"数据科学与大数据技术2401", "2024级", "38"},
            {"网络工程2401", "2024级", "40"},
    };

    /** 演示课程: [课程代码, 课程名称, 课程类型, 要求教室类型] */
    private static final String[][] DEMO_COURSES = {
            {"CS2201", "程序设计基础", Constants.COURSE_THEORY, Constants.ROOM_MULTIMEDIA},
            {"CS3202", "数据结构与算法", Constants.COURSE_THEORY, Constants.ROOM_MULTIMEDIA},
            {"SE4301", "软件工程导论", Constants.COURSE_THEORY, Constants.ROOM_MULTIMEDIA},
            {"DB3103", "数据库原理", Constants.COURSE_THEORY, Constants.ROOM_MULTIMEDIA},
            {"OS3304", "操作系统", Constants.COURSE_THEORY, Constants.ROOM_NORMAL},
            {"CS2250", "程序设计基础实验", Constants.COURSE_LAB, Constants.ROOM_LAB},
            {"CS3251", "数据结构实验", Constants.COURSE_LAB, Constants.ROOM_LAB},
            {"SE4350", "软件工程综合实践", Constants.COURSE_LAB, Constants.ROOM_LAB},
    };

    /** 演示教室: [编号, 楼栋, 容量, 类型] */
    private static final String[][] DEMO_CLASSROOMS = {
            {"N101", "教学楼A", "70", Constants.ROOM_NORMAL},
            {"N102", "教学楼A", "60", Constants.ROOM_NORMAL},
            {"N201", "教学楼B", "80", Constants.ROOM_MULTIMEDIA},
            {"N202", "教学楼B", "80", Constants.ROOM_MULTIMEDIA},
            {"N203", "教学楼B", "60", Constants.ROOM_MULTIMEDIA},
            {"N301", "教学楼B", "100", Constants.ROOM_MULTIMEDIA},
            {"L101", "实验楼A", "45", Constants.ROOM_LAB},
            {"L102", "实验楼A", "45", Constants.ROOM_LAB},
            {"L201", "实验楼B", "50", Constants.ROOM_LAB},
            {"L202", "实验楼B", "50", Constants.ROOM_LAB},
    };

    /**
     * 演示开课实例(共 17 条, 覆盖理论/多媒体/LAB, weeklySessions 1 与 2):
     * [课程下标, 教师下标, 班级下标, weeklySessions, durationSlots]
     * 下标对齐上方 DEMO_COURSES / DEMO_TEACHERS / DEMO_CLASSES。
     */
    private static final int[][] DEMO_OFFERINGS = {
            {0, 0, 0, 2, 1},  // CS2201 程序设计基础 陈明理 软工2401
            {0, 0, 4, 2, 1},  // CS2201 程序设计基础 陈明理 计科2402
            {0, 0, 1, 1, 1},  // CS2201 程序设计基础 陈明理 软工2402 (weeklySessions=1)
            {1, 1, 0, 2, 1},  // CS3202 数据结构与算法 李慧敏 软工2401
            {1, 1, 4, 2, 1},  // CS3202 数据结构与算法 李慧敏 计科2402
            {2, 2, 1, 2, 1},  // SE4301 软件工程导论 刘雅琴 软工2402
            {2, 2, 2, 1, 1},  // SE4301 软件工程导论 刘雅琴 软工2403 (weeklySessions=1)
            {3, 4, 5, 2, 1},  // DB3103 数据库原理 周启航 数据2401
            {3, 4, 3, 2, 1},  // DB3103 数据库原理 周启航 计科2401
            {4, 5, 6, 1, 1},  // OS3304 操作系统 王建斌 网工2401
            {5, 1, 0, 1, 2},  // CS2250 程序设计基础实验(2节连续) 李慧敏 软工2401
            {5, 1, 1, 1, 2},  // CS2250 程序设计基础实验(2节连续) 李慧敏 软工2402
            {5, 2, 3, 1, 2},  // CS2250 程序设计基础实验(2节连续) 刘雅琴 计科2401
            {6, 3, 4, 1, 2},  // CS3251 数据结构实验(2节连续) 张晓东 计科2402
            {6, 3, 5, 1, 2},  // CS3251 数据结构实验(2节连续) 张晓东 数据2401
            {7, 5, 2, 1, 2},  // SE4350 软件工程综合实践(2节连续) 王建斌 软工2403
            {7, 5, 6, 1, 2},  // SE4350 软件工程综合实践(2节连续) 王建斌 网工2401
    };

    /** 演示教师偏好(S1 软约束演示): [教师下标, 周几(1-5), 大节(1-5), 偏好级别(1/-1)] */
    private static final int[][] DEMO_PREFERENCES = {
            {0, 1, 1, 1},   // 陈明理 偏好周一第1-2节
            {0, 3, 1, -1},  // 陈明理 不偏好周三第1-2节
            {1, 2, 1, 1},   // 李慧敏 偏好周二第1-2节
            {1, 4, 5, -1},  // 李慧敏 不偏好周四第9-10节
            {2, 3, 3, 1},   // 刘雅琴 偏好周三第5-6节
            {3, 1, 5, -1},  // 张晓东 不偏好周一第9-10节
            {4, 4, 2, 1},   // 周启航 偏好周四第3-4节
            {5, 5, 1, -1},  // 王建斌 不偏好周五第1-2节
    };

    /** 演示资源不可用(少量, 只约束候选中的极少数时段, 不破坏演示任务可行性): [类型, 资源下标, 周几, 大节, 原因] */
    private static final String[][] DEMO_UNAVAILABILITIES = {
            {Constants.RESOURCE_TEACHER, "0", "1", "5", "周一例会"},
            {Constants.RESOURCE_TEACHER, "1", "3", "5", "周三学术会议"},
            {Constants.RESOURCE_CLASS, "0", "5", "5", "周五班级活动"},
            {Constants.RESOURCE_CLASSROOM, "2", "1", "5", "N201 周一设备维护"},
            {Constants.RESOURCE_CLASSROOM, "6", "2", "5", "L101 周二设备维护"},
    };

    // ---------- 依赖 ----------

    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRepository courseRepository;
    private final ClazzRepository clazzRepository;
    private final ClassroomRepository classroomRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseOfferingClassRepository courseOfferingClassRepository;
    private final TeacherPreferenceRepository teacherPreferenceRepository;
    private final ResourceUnavailabilityRepository resourceUnavailabilityRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final SchedulingTaskRepository schedulingTaskRepository;
    private final SchedulingTaskCourseRepository schedulingTaskCourseRepository;
    private final SchedulingTaskClassroomRepository schedulingTaskClassroomRepository;

    /**
     * 若演示数据尚未初始化则整体插入并返回 true; 已初始化则跳过并返回 false。
     * 单事务、幂等、只插入不覆盖(详见类注释)。
     */
    @Transactional
    public boolean initializeIfNeeded() {
        if (courseRepository.existsByCourseCode(MARKER_COURSE_CODE)) {
            log.info("演示数据已存在(标记课程 {}), 跳过初始化: 不会重复插入任何数据", MARKER_COURSE_CODE);
            return false;
        }
        insertDemoData();
        log.info("演示数据初始化完成: 学期={}, 任务『{}』已就绪(PENDING, 可直接执行排课)",
                DEMO_SEMESTER, DEMO_TASK_NAME);
        return true;
    }

    // ------------------------------------------------------------------
    // 数据构建(全部依赖顺序在方法内显式保证; 单事务失败整体回滚)
    // ------------------------------------------------------------------

    private void insertDemoData() {
        // 0. time_slot 自补全(与 TimeSlotDataInitializer 语义一致, 本服务不依赖 runner 顺序)
        Map<String, Long> slotIdByDayPeriod = ensureTimeSlots();

        // 1. 教师登录账号 + 教师
        List<User> teacherUsers = new ArrayList<>();
        for (String[] t : DEMO_TEACHERS) {
            User user = saveIfAbsentTeacherUser(t[4], t[1]);
            teacherUsers.add(user);
        }
        List<Teacher> teachers = new ArrayList<>();
        for (int i = 0; i < DEMO_TEACHERS.length; i++) {
            teachers.add(teacherRepository.save(Teacher.builder()
                    .userId(teacherUsers.get(i).getId())
                    .teacherNo(DEMO_TEACHERS[i][0])
                    .name(DEMO_TEACHERS[i][1])
                    .title(DEMO_TEACHERS[i][2])
                    .department(DEMO_TEACHERS[i][3])
                    .build()));
        }

        // 2. 班级
        List<Clazz> clazzes = new ArrayList<>();
        for (String[] c : DEMO_CLASSES) {
            clazzes.add(clazzRepository.save(Clazz.builder()
                    .className(c[0])
                    .grade(c[1])
                    .studentCount(Integer.parseInt(c[2]))
                    .department("软件学院")
                    .build()));
        }

        // 3. 课程
        List<Course> courses = new ArrayList<>();
        for (String[] c : DEMO_COURSES) {
            courses.add(courseRepository.save(Course.builder()
                    .courseCode(c[0])
                    .courseName(c[1])
                    .courseType(c[2])
                    .requiredRoomType(c[3])
                    .build()));
        }

        // 4. 教室
        List<Classroom> classrooms = new ArrayList<>();
        for (String[] r : DEMO_CLASSROOMS) {
            classrooms.add(classroomRepository.save(Classroom.builder()
                    .roomNo(r[0])
                    .building(r[1])
                    .capacity(Integer.parseInt(r[2]))
                    .roomType(r[3])
                    .build()));
        }

        // 5. 开课实例 + 开课-班级关联
        List<CourseOffering> offerings = new ArrayList<>();
        List<CourseOfferingClass> offeringClasses = new ArrayList<>();
        for (int[] row : DEMO_OFFERINGS) {
            CourseOffering offering = courseOfferingRepository.save(CourseOffering.builder()
                    .courseId(courses.get(row[0]).getId())
                    .teacherId(teachers.get(row[1]).getId())
                    .semester(DEMO_SEMESTER)
                    .weeklySessions(row[3])
                    .durationSlots(row[4])
                    .isLabCourse(row[4] > 1 ? 1 : 0)
                    .build());
            offerings.add(offering);
            offeringClasses.add(CourseOfferingClass.builder()
                    .courseOfferingId(offering.getId())
                    .classId(clazzes.get(row[2]).getId())
                    .build());
        }
        courseOfferingClassRepository.saveAll(offeringClasses);

        // 6. 教师偏好(S1)
        List<TeacherPreference> preferences = new ArrayList<>();
        for (int[] p : DEMO_PREFERENCES) {
            Long slotId = slotIdByDayPeriod.get(key(p[1], p[2]));
            preferences.add(TeacherPreference.builder()
                    .teacherId(teachers.get(p[0]).getId())
                    .timeSlotId(slotId)
                    .preferenceLevel(p[3])
                    .build());
        }
        teacherPreferenceRepository.saveAll(preferences);

        // 7. 资源不可用(系统级, 少量且不破坏演示任务可行性)
        List<ResourceUnavailability> unavailabilities = new ArrayList<>();
        for (String[] u : DEMO_UNAVAILABILITIES) {
            Long resourceId;
            if (Constants.RESOURCE_TEACHER.equals(u[0])) {
                resourceId = teachers.get(Integer.parseInt(u[1])).getId();
            } else if (Constants.RESOURCE_CLASS.equals(u[0])) {
                resourceId = clazzes.get(Integer.parseInt(u[1])).getId();
            } else {
                resourceId = classrooms.get(Integer.parseInt(u[1])).getId();
            }
            unavailabilities.add(ResourceUnavailability.builder()
                    .resourceType(u[0])
                    .resourceId(resourceId)
                    .timeSlotId(slotIdByDayPeriod.get(
                            key(Integer.parseInt(u[2]), Integer.parseInt(u[3]))))
                    .reason(u[4])
                    .build());
        }
        resourceUnavailabilityRepository.saveAll(unavailabilities);

        // 8. 演示排课任务(PENDING): 纳入全部演示开课实例 + 全部演示教室
        SchedulingTask task = schedulingTaskRepository.save(SchedulingTask.builder()
                .taskName(DEMO_TASK_NAME)
                .semester(DEMO_SEMESTER)
                .weekCount(16)
                .createTime(LocalDateTime.now())
                .build());
        List<SchedulingTaskCourse> taskCourses = new ArrayList<>();
        for (CourseOffering offering : offerings) {
            taskCourses.add(SchedulingTaskCourse.builder()
                    .schedulingTaskId(task.getId())
                    .courseOfferingId(offering.getId())
                    .build());
        }
        List<SchedulingTaskClassroom> taskClassrooms = new ArrayList<>();
        for (Classroom classroom : classrooms) {
            taskClassrooms.add(SchedulingTaskClassroom.builder()
                    .schedulingTaskId(task.getId())
                    .classroomId(classroom.getId())
                    .build());
        }
        schedulingTaskCourseRepository.saveAll(taskCourses);
        schedulingTaskClassroomRepository.saveAll(taskClassrooms);
    }

    /** 教师登录账号: 已存在则复用, 不存在才创建 —— 绝不重复建号 */
    private User saveIfAbsentTeacherUser(String username, String realName) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(username)
                        .password(PASSWORD_ENCODER.encode(DEMO_ACCOUNT_PASSWORD))
                        .realName(realName)
                        .role(Constants.ROLE_TEACHER)
                        .status(1)
                        .build()));
    }

    /** 确保 25 个标准时间段存在(与 schema.sql / TimeSlotDataInitializer 约定一致, 幂等) */
    private Map<String, Long> ensureTimeSlots() {
        String[] starts = {"08:00", "10:00", "14:00", "16:00", "19:00"};
        String[] ends = {"09:35", "11:35", "15:35", "17:35", "20:35"};
        Map<String, Long> slotIdByDayPeriod = new HashMap<>();
        for (int day = 1; day <= 5; day++) {
            for (int period = 1; period <= 5; period++) {
                final int d = day;
                final int p = period;
                TimeSlot slot = timeSlotRepository.findByDayOfWeekAndPeriod(d, p)
                        .orElseGet(() -> newTimeSlot(d, p, starts, ends));
                slotIdByDayPeriod.put(key(d, p), slot.getId());
            }
        }
        return slotIdByDayPeriod;
    }

    private TimeSlot newTimeSlot(int day, int period, String[] starts, String[] ends) {
        return timeSlotRepository.save(TimeSlot.builder()
                .dayOfWeek(day)
                .period(period)
                .startTime(starts[period - 1])
                .endTime(ends[period - 1])
                .build());
    }

    private static String key(int day, int period) {
        return day + "-" + period;
    }
}
