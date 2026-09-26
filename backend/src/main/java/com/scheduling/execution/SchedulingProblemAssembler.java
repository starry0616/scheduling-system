package com.scheduling.execution;

import com.scheduling.algorithm.CandidateAssignment;
import com.scheduling.algorithm.CandidateBuilder;
import com.scheduling.algorithm.RoomInfo;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SchedulingUnit;
import com.scheduling.algorithm.TimeSlotInfo;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.TimeSlot;
import com.scheduling.vo.CourseOfferingData;
import com.scheduling.vo.SchedulingTaskData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SchedulingProblem 组装器(阶段六, 纯"数据库视图 → 算法模型"转换)。
 *
 * <p>职责: 把 {@link SchedulingTaskData}(含任务范围快照 + offering/course/teacher/classes +
 * 教室池 + 时间段) 与已转换为纯 Map/Set 的资源不可用时间、教师偏好, 组装成算法层
 * {@link SchedulingProblem}:
 * <pre>
 *   每个 CourseOffering 按 weeklySessions 展开为 k 个 SchedulingUnit(sessionIndex=0..k-1);
 *   每个 Unit 的 candidates 由 CandidateBuilder 预计算(容量/教室类型/不可用时间全部在
 *   候选生成阶段过滤, 不做软惩罚);
 *   不可行数据(无 offering/无教室池/无时间段/candidates 为空/weeklySessions&gt;可用天数/
 *   引用损坏)在此阶段以 InfeasibleDataException 提前检出, 绝不进入模拟退火。
 * </pre>
 *
 * <p>本类只依赖纯算法 POJO 与业务数据视图, 不触达任何 Repository / JPA 实体。
 */
@Component
public class SchedulingProblemAssembler {

    // 与 SchedulingTask 实体默认值一致的权重缺省(理论上前端/服务层已写入非空值)
    private static final int DEFAULT_W_TEACHER_PREFERENCE = 50;
    private static final int DEFAULT_W_COURSE_DISTRIBUTION = 30;
    private static final int DEFAULT_W_STUDENT_BALANCE = 25;
    private static final int DEFAULT_W_TEACHER_CONTINUOUS = 30;
    private static final int DEFAULT_W_STUDENT_IDLE = 25;
    private static final int DEFAULT_W_MORNING_EVENING = 15;

    /**
     * @param data               任务数据视图(SchedulingTaskService.getSchedulingTaskData 产出)
     * @param teacherPreferences 教师偏好 teacherId -&gt; (timeSlotId -&gt; level), 缺失视为 0
     * @param teacherBlocked     教师不可用 teacherId -&gt; 不可用 slotId 集合
     * @param classBlocked       班级不可用 classId -&gt; 不可用 slotId 集合
     * @param classroomBlocked   教室不可用 classroomId -&gt; 不可用 slotId 集合
     */
    public SchedulingProblem assemble(SchedulingTaskData data,
                                      Map<Long, Map<Long, Integer>> teacherPreferences,
                                      Map<Long, Set<Long>> teacherBlocked,
                                      Map<Long, Set<Long>> classBlocked,
                                      Map<Long, Set<Long>> classroomBlocked) {
        try {
            return doAssemble(data, teacherPreferences, teacherBlocked, classBlocked, classroomBlocked);
        } catch (InfeasibleDataException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new InfeasibleDataException(
                    "任务数据无法构成合法的排课问题: " + e.getMessage());
        }
    }

    private SchedulingProblem doAssemble(SchedulingTaskData data,
                                         Map<Long, Map<Long, Integer>> teacherPreferences,
                                         Map<Long, Set<Long>> teacherBlocked,
                                         Map<Long, Set<Long>> classBlocked,
                                         Map<Long, Set<Long>> classroomBlocked) {
        SchedulingTask task = data.getTask();
        List<CourseOfferingData> offeringDataList = data.getOfferings();
        if (offeringDataList == null || offeringDataList.isEmpty()) {
            throw new InfeasibleDataException(
                    "任务未纳入任何开课实例, taskId=" + task.getId());
        }
        List<Classroom> classroomPool = data.getClassrooms();
        if (classroomPool == null || classroomPool.isEmpty()) {
            throw new InfeasibleDataException(
                    "任务未配置可用教室池, taskId=" + task.getId());
        }
        List<TimeSlot> timeSlotEntities = data.getTimeSlots();
        if (timeSlotEntities == null || timeSlotEntities.isEmpty()) {
            throw new InfeasibleDataException(
                    "系统中不存在任何时间段数据(time_slot 为空)");
        }

        // 1. 时间段: 按 (dayOfWeek, period) 升序的稳定时间轴(连续性判断禁止 slotId+1)
        List<TimeSlotInfo> timeSlotInfos = timeSlotEntities.stream()
                .map(s -> new TimeSlotInfo(s.getId(), s.getDayOfWeek(), s.getPeriod()))
                .sorted((a, b) -> a.getDayOfWeek() != b.getDayOfWeek()
                        ? Integer.compare(a.getDayOfWeek(), b.getDayOfWeek())
                        : Integer.compare(a.getPeriod(), b.getPeriod()))
                .toList();
        Set<Integer> daySet = new HashSet<>();
        for (TimeSlotInfo slot : timeSlotInfos) {
            daySet.add(slot.getDayOfWeek());
        }
        int availableDays = daySet.size();

        // 2. 教室池
        List<RoomInfo> rooms = new ArrayList<>();
        for (Classroom room : classroomPool) {
            if (room.getCapacity() == null || room.getCapacity() < 1) {
                throw new InfeasibleDataException(
                        "教室容量配置非法(必须 >= 1): classroomId=" + room.getId()
                                + ", capacity=" + room.getCapacity());
            }
            rooms.add(new RoomInfo(room.getId(), room.getCapacity(), room.getRoomType()));
        }

        // 3. 展开 SchedulingUnit
        List<SchedulingUnit> units = new ArrayList<>();
        int globalUnitIndex = 0;
        for (CourseOfferingData offeringData : offeringDataList) {
            CourseOffering offering = offeringData.getOffering();
            if (offering == null) {
                throw new InfeasibleDataException("任务数据不一致: offering 为空, taskId=" + task.getId());
            }
            Course course = offeringData.getCourse();
            if (course == null) {
                throw new InfeasibleDataException(
                        "开课实例缺少课程元数据(可能 course_id 引用已失效), courseOfferingId="
                                + offering.getId());
            }
            List<Clazz> classes = offeringData.getClasses();
            if (classes == null || classes.isEmpty()) {
                throw new InfeasibleDataException(
                        "开课实例未配置任何授课班级, courseOfferingId=" + offering.getId());
            }

            int weeklySessions = offering.getWeeklySessions() == null ? 1 : offering.getWeeklySessions();
            int durationSlots = offering.getDurationSlots() == null ? 1 : offering.getDurationSlots();
            if (weeklySessions < 1 || durationSlots < 1) {
                throw new InfeasibleDataException(
                        "开课实例排课属性非法(weeklySessions/durationSlots 必须为正): courseOfferingId="
                                + offering.getId());
            }
            if (weeklySessions > availableDays) {
                throw new InfeasibleDataException(
                        "每周课次数超过可用工作日数: courseOfferingId=" + offering.getId()
                                + ", weeklySessions=" + weeklySessions + ", 可用工作日=" + availableDays);
            }

            int totalStudents = 0;
            long[] classIds = new long[classes.size()];
            Set<Long> classUnionBlocked = new HashSet<>();
            for (int ci = 0; ci < classes.size(); ci++) {
                Clazz clazz = classes.get(ci);
                if (clazz.getStudentCount() == null || clazz.getStudentCount() < 0) {
                    throw new InfeasibleDataException(
                            "班级人数配置非法: classId=" + clazz.getId()
                                    + ", studentCount=" + clazz.getStudentCount());
                }
                totalStudents += clazz.getStudentCount();
                classIds[ci] = clazz.getId();
                Set<Long> blocked = classBlocked.get(clazz.getId());
                if (blocked != null) {
                    classUnionBlocked.addAll(blocked);
                }
            }

            Set<Long> teacherUnavailable = teacherBlocked.getOrDefault(offering.getTeacherId(), Set.of());
            String requiredRoomType = normalizeRoomType(course.getRequiredRoomType());

            List<CandidateAssignment> candidates = CandidateBuilder.buildCandidates(
                    rooms,
                    timeSlotInfos,
                    totalStudents,
                    requiredRoomType,
                    durationSlots,
                    teacherUnavailable,
                    classUnionBlocked,
                    classroomBlocked);

            if (candidates.isEmpty()) {
                throw new InfeasibleDataException(
                        "开课实例没有任何合法(教室,时间)组合, courseOfferingId=" + offering.getId()
                                + ", weeklySessions=" + weeklySessions
                                + ", durationSlots=" + durationSlots
                                + ", totalStudents=" + totalStudents
                                + ", requiredRoomType=" + requiredRoomType
                                + " — 请检查教室容量/类型是否匹配, 以及教师/班级/教室的不可用时间配置");
            }

            for (int sessionIndex = 0; sessionIndex < weeklySessions; sessionIndex++) {
                units.add(new SchedulingUnit(
                        globalUnitIndex++,
                        offering.getId(),
                        sessionIndex,
                        offering.getTeacherId(),
                        classIds,
                        totalStudents,
                        requiredRoomType,
                        weeklySessions,
                        durationSlots,
                        candidates));
            }
        }

        // 4. 组装 SchedulingProblem(W_hard = floor(SoftMax)+1 在构造器内推导)
        return new SchedulingProblem(
                units,
                timeSlotInfos,
                teacherPreferences,
                intOf(task.getWTeacherPreference(), DEFAULT_W_TEACHER_PREFERENCE),
                intOf(task.getWCourseDistribution(), DEFAULT_W_COURSE_DISTRIBUTION),
                intOf(task.getWStudentBalance(), DEFAULT_W_STUDENT_BALANCE),
                intOf(task.getWTeacherContinuous(), DEFAULT_W_TEACHER_CONTINUOUS),
                intOf(task.getWStudentIdle(), DEFAULT_W_STUDENT_IDLE),
                intOf(task.getWMorningEvening(), DEFAULT_W_MORNING_EVENING));
    }

    /** 教室类型规范化: 空值按 NORMAL 处理, 其余统一大写(与数据库取值一致) */
    private String normalizeRoomType(String type) {
        if (type == null || type.isBlank()) {
            return "NORMAL";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private int intOf(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
