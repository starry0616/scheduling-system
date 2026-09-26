package com.scheduling.service;

import com.scheduling.common.Constants;
import com.scheduling.dto.ClassroomResponse;
import com.scheduling.dto.CourseOfferingOptionResponse;
import com.scheduling.dto.CourseOfferingResponse;
import com.scheduling.dto.SchedulingTaskClassroomRequest;
import com.scheduling.dto.SchedulingTaskOfferingRequest;
import com.scheduling.dto.SchedulingTaskRequest;
import com.scheduling.dto.SchedulingTaskResponse;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.CourseOfferingClass;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.SchedulingTaskClassroom;
import com.scheduling.entity.SchedulingTaskCourse;
import com.scheduling.entity.Teacher;
import com.scheduling.entity.TimeSlot;
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
import com.scheduling.vo.CourseOfferingData;
import com.scheduling.vo.SchedulingTaskData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排课任务管理服务 (第3步第三阶段 3C-3)
 *
 * 领域语义: SchedulingTask = 一次排课请求的任务壳(范围 + SA参数 + 软约束权重),
 * scheduling_task_course / scheduling_task_classroom 表达任务的排课范围快照。
 * 本阶段只做任务管理与范围维护, 不触碰 SchedulingUnit/算法/执行/结果:
 *   - hardConstraintWeight 创建恒为 null, 不按课程数量提前计算;
 *   - 无任何状态迁移接口, 创建后固定 PENDING(RUNNING/COMPLETED/FAILED 语义留执行阶段)。
 *
 * 状态保护: 修改 / 删除 / 修改两个范围 仅允许 PENDING;
 * 删除任务: 事务内先批量清 scheduling_task_course / scheduling_task_classroom,
 * 再删任务本体(对应 schema.sql 中 task 侧 ON DELETE CASCADE 意图, 服务层显式保证)。
 *
 * 范围保存: 覆盖式(先删旧关联再插入新关联, 事务内幂等); 引用资源必须存在且
 * 开课实例学期必须与任务学期一致(禁止跨学期纳入); 空集合表示清空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingTaskService {

    /** 可接受的排课任务状态 */
    private static final Set<String> TASK_STATUSES = Set.of(
            Constants.TASK_PENDING, Constants.TASK_RUNNING,
            Constants.TASK_COMPLETED, Constants.TASK_FAILED);

    private static final int DEFAULT_WEEK_COUNT = 16;
    private static final int DEFAULT_W_TEACHER_PREFERENCE = 50;
    private static final int DEFAULT_W_COURSE_DISTRIBUTION = 30;
    private static final int DEFAULT_W_STUDENT_BALANCE = 25;
    private static final int DEFAULT_W_TEACHER_CONTINUOUS = 30;
    private static final int DEFAULT_W_STUDENT_IDLE = 25;
    private static final int DEFAULT_W_MORNING_EVENING = 15;

    private final SchedulingTaskRepository schedulingTaskRepository;
    private final SchedulingTaskCourseRepository schedulingTaskCourseRepository;
    private final SchedulingTaskClassroomRepository schedulingTaskClassroomRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseOfferingClassRepository courseOfferingClassRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final ClassroomRepository classroomRepository;
    private final ClazzRepository clazzRepository;
    private final TimeSlotRepository timeSlotRepository;

    // ---------- 任务 CRUD ----------

    /** 任务列表, 可按学期/状态精确过滤(均缺省返回全部), 新任务在前 */
    public List<SchedulingTaskResponse> list(String semester, String status) {
        String semesterFilter = normalizeOptional(semester);
        String statusFilter = normalizeOptional(status);
        if (statusFilter != null && !TASK_STATUSES.contains(statusFilter)) {
            throw new BusinessException(400, "非法任务状态, 仅支持 PENDING/RUNNING/COMPLETED/FAILED");
        }
        List<SchedulingTask> tasks;
        if (semesterFilter == null && statusFilter == null) {
            tasks = schedulingTaskRepository.findAllByOrderByIdDesc();
        } else if (semesterFilter == null) {
            tasks = schedulingTaskRepository.findByStatusOrderByIdDesc(statusFilter);
        } else if (statusFilter == null) {
            tasks = schedulingTaskRepository.findBySemesterOrderByIdDesc(semesterFilter);
        } else {
            tasks = schedulingTaskRepository.findBySemesterAndStatusOrderByIdDesc(semesterFilter, statusFilter);
        }
        return tasks.stream().map(SchedulingTaskResponse::from).toList();
    }

    /** 任务详情 */
    public SchedulingTaskResponse getById(Long id) {
        return SchedulingTaskResponse.from(getEntity(id));
    }

    /**
     * 新增任务: 状态固定 PENDING、hardConstraintWeight 恒为 null(系统后续自动计算),
     * 缺省数值参数走系统默认值。
     */
    @Transactional
    public SchedulingTaskResponse create(SchedulingTaskRequest request) {
        SchedulingTask task = SchedulingTask.builder()
                .taskName(request.getTaskName().trim())
                .semester(request.getSemester().trim())
                .weekCount(defaultInt(request.getWeekCount(), DEFAULT_WEEK_COUNT))
                .status(Constants.TASK_PENDING)
                .maxInitialTemp(defaultDouble(request.getMaxInitialTemp(), Constants.DEFAULT_MAX_INITIAL_TEMP))
                .minInitialTemp(defaultDouble(request.getMinInitialTemp(), Constants.DEFAULT_MIN_INITIAL_TEMP))
                .minTemp(defaultDouble(request.getMinTemp(), Constants.DEFAULT_MIN_TEMP))
                .coolingRate(defaultDouble(request.getCoolingRate(), Constants.DEFAULT_COOLING_RATE))
                .maxTempIterations(defaultInt(request.getMaxTempIterations(), Constants.DEFAULT_MAX_TEMP_ITERATIONS))
                .neighborsPerTemp(defaultInt(request.getNeighborsPerTemp(), Constants.DEFAULT_NEIGHBORS_PER_TEMP))
                .maxRepairAttempts(defaultInt(request.getMaxRepairAttempts(), Constants.DEFAULT_MAX_REPAIR_ATTEMPTS))
                .randomSeed(request.getRandomSeed())
                .hardConstraintWeight(null)
                .wTeacherPreference(defaultInt(request.getWTeacherPreference(), DEFAULT_W_TEACHER_PREFERENCE))
                .wCourseDistribution(defaultInt(request.getWCourseDistribution(), DEFAULT_W_COURSE_DISTRIBUTION))
                .wStudentBalance(defaultInt(request.getWStudentBalance(), DEFAULT_W_STUDENT_BALANCE))
                .wTeacherContinuous(defaultInt(request.getWTeacherContinuous(), DEFAULT_W_TEACHER_CONTINUOUS))
                .wStudentIdle(defaultInt(request.getWStudentIdle(), DEFAULT_W_STUDENT_IDLE))
                .wMorningEvening(defaultInt(request.getWMorningEvening(), DEFAULT_W_MORNING_EVENING))
                .createTime(LocalDateTime.now())
                .finishTime(null)
                .build();
        schedulingTaskRepository.save(task);
        log.info("新增排课任务成功: id={}, taskName={}, semester={}", task.getId(), task.getTaskName(), task.getSemester());
        return SchedulingTaskResponse.from(task);
    }

    /**
     * 修改任务: 仅 PENDING 允许; 请求不含 status/hardConstraintWeight(系统字段),
     * 二者不会被动修改; 数值参数为 null 表示保持现值(支持部分修改)。
     */
    @Transactional
    public SchedulingTaskResponse update(Long id, SchedulingTaskRequest request) {
        SchedulingTask task = requirePendingForWrite(getEntity(id), "修改");
        task.setTaskName(request.getTaskName().trim());
        task.setSemester(request.getSemester().trim());
        if (request.getWeekCount() != null) {
            task.setWeekCount(request.getWeekCount());
        }
        if (request.getMaxInitialTemp() != null) {
            task.setMaxInitialTemp(request.getMaxInitialTemp());
        }
        if (request.getMinInitialTemp() != null) {
            task.setMinInitialTemp(request.getMinInitialTemp());
        }
        if (request.getMinTemp() != null) {
            task.setMinTemp(request.getMinTemp());
        }
        if (request.getCoolingRate() != null) {
            task.setCoolingRate(request.getCoolingRate());
        }
        if (request.getMaxTempIterations() != null) {
            task.setMaxTempIterations(request.getMaxTempIterations());
        }
        if (request.getNeighborsPerTemp() != null) {
            task.setNeighborsPerTemp(request.getNeighborsPerTemp());
        }
        if (request.getMaxRepairAttempts() != null) {
            task.setMaxRepairAttempts(request.getMaxRepairAttempts());
        }
        if (request.getRandomSeed() != null) {
            task.setRandomSeed(request.getRandomSeed());
        }
        if (request.getWTeacherPreference() != null) {
            task.setWTeacherPreference(request.getWTeacherPreference());
        }
        if (request.getWCourseDistribution() != null) {
            task.setWCourseDistribution(request.getWCourseDistribution());
        }
        if (request.getWStudentBalance() != null) {
            task.setWStudentBalance(request.getWStudentBalance());
        }
        if (request.getWTeacherContinuous() != null) {
            task.setWTeacherContinuous(request.getWTeacherContinuous());
        }
        if (request.getWStudentIdle() != null) {
            task.setWStudentIdle(request.getWStudentIdle());
        }
        if (request.getWMorningEvening() != null) {
            task.setWMorningEvening(request.getWMorningEvening());
        }
        schedulingTaskRepository.save(task);
        log.info("修改排课任务成功: id={}", id);
        return SchedulingTaskResponse.from(task);
    }

    /**
     * 删除任务: 仅 PENDING 允许; 事务内先批量清两张关联表再删任务本体,
     * 与 schema.sql 中 task 侧 ON DELETE CASCADE 意图保持一致。
     */
    @Transactional
    public void delete(Long id) {
        SchedulingTask task = requirePendingForWrite(getEntity(id), "删除");
        schedulingTaskCourseRepository.deleteBySchedulingTaskId(id);
        schedulingTaskClassroomRepository.deleteBySchedulingTaskId(id);
        schedulingTaskRepository.delete(task);
        log.info("删除排课任务成功: id={}", id);
    }

    // ---------- 任务-开课实例范围 ----------

    /** 查询任务已纳入的开课实例(task 不存在 -> code404), 按纳入顺序返回 */
    public List<CourseOfferingResponse> listCourseOfferings(Long taskId) {
        getEntity(taskId);
        List<SchedulingTaskCourse> links = schedulingTaskCourseRepository
                .findBySchedulingTaskIdOrderByIdAsc(taskId);
        return toOfferingResponses(links);
    }

    /**
     * 覆盖式保存任务纳入的开课实例: 先删旧关联再写入新关联(事务内幂等);
     * 空=清空; 重复自动去重; 所有 offering 必须存在且 semester 必须与任务一致,
     * 任一不满足整体拒绝(不产生半更新)。
     */
    @Transactional
    public List<CourseOfferingResponse> replaceCourseOfferings(Long taskId,
                                                               SchedulingTaskOfferingRequest request) {
        SchedulingTask task = requirePendingForWrite(getEntity(taskId), "修改课程范围");
        List<Long> rawIds = request.getCourseOfferingIds();
        if (rawIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(400, "开课实例ID不能为空");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(rawIds));

        if (!distinctIds.isEmpty()) {
            Map<Long, CourseOffering> offeringMap = courseOfferingRepository.findAllById(distinctIds).stream()
                    .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a));
            for (Long offeringId : distinctIds) {
                CourseOffering offering = offeringMap.get(offeringId);
                if (offering == null) {
                    throw new BusinessException(404, "引用的开课实例不存在: id=" + offeringId);
                }
                if (!task.getSemester().equals(offering.getSemester())) {
                    throw new BusinessException(400,
                            "开课实例学期与任务学期不一致: offeringId=" + offeringId
                                    + "(offering.semester=" + offering.getSemester()
                                    + ", task.semester=" + task.getSemester() + "), 禁止跨学期纳入任务");
                }
            }
        }

        // 覆盖式: 先删旧关联再插入新关联(去重保证无唯一键冲突)
        schedulingTaskCourseRepository.deleteBySchedulingTaskId(taskId);
        if (!distinctIds.isEmpty()) {
            List<SchedulingTaskCourse> links = distinctIds.stream()
                    .map(offeringId -> SchedulingTaskCourse.builder()
                            .schedulingTaskId(taskId)
                            .courseOfferingId(offeringId)
                            .build())
                    .toList();
            schedulingTaskCourseRepository.saveAll(links);
        }
        log.info("保存排课任务-开课实例范围成功: taskId={}, offeringCount={}", taskId, distinctIds.size());
        return listCourseOfferings(taskId);
    }

    // ---------- 任务-教室范围 ----------

    /** 查询任务可用教室池(task 不存在 -> code404), 按纳入顺序返回 */
    public List<ClassroomResponse> listClassrooms(Long taskId) {
        getEntity(taskId);
        List<SchedulingTaskClassroom> links = schedulingTaskClassroomRepository
                .findBySchedulingTaskIdOrderByIdAsc(taskId);
        return toClassroomResponses(links);
    }

    /**
     * 覆盖式保存任务可用教室池: 先删旧关联再写入新关联(事务内幂等);
     * 空=清空; 重复自动去重; 所有教室必须存在, 任一缺失整体拒绝。
     */
    @Transactional
    public List<ClassroomResponse> replaceClassrooms(Long taskId,
                                                     SchedulingTaskClassroomRequest request) {
        requirePendingForWrite(getEntity(taskId), "修改教室范围");
        List<Long> rawIds = request.getClassroomIds();
        if (rawIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(400, "教室ID不能为空");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(rawIds));

        if (!distinctIds.isEmpty()) {
            Map<Long, Classroom> classroomMap = classroomRepository.findAllById(distinctIds).stream()
                    .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
            for (Long classroomId : distinctIds) {
                if (!classroomMap.containsKey(classroomId)) {
                    throw new BusinessException(404, "引用的教室不存在: id=" + classroomId);
                }
            }
        }

        schedulingTaskClassroomRepository.deleteBySchedulingTaskId(taskId);
        if (!distinctIds.isEmpty()) {
            List<SchedulingTaskClassroom> links = distinctIds.stream()
                    .map(classroomId -> SchedulingTaskClassroom.builder()
                            .schedulingTaskId(taskId)
                            .classroomId(classroomId)
                            .build())
                    .toList();
            schedulingTaskClassroomRepository.saveAll(links);
        }
        log.info("保存排课任务-教室范围成功: taskId={}, classroomCount={}", taskId, distinctIds.size());
        return listClassrooms(taskId);
    }

    // ---------- 任务课程配置候选(阶段七前端接入: 展示同学期可纳入的开课, 只读) ----------

    /**
     * 返回与任务同一学期的全部开课实例候选(附带班级/课程信息),
     * 供前端"配置课程-添加"弹窗选择; 不区分是否已在任务范围内(由前端标记已选)。
     */
    public List<CourseOfferingOptionResponse> listOfferingOptions(Long taskId) {
        SchedulingTask task = getEntity(taskId);
        List<CourseOffering> offerings = courseOfferingRepository
                .findBySemesterOrderByIdDesc(task.getSemester());
        if (offerings.isEmpty()) {
            return List.of();
        }
        List<Long> offeringIds = offerings.stream().map(CourseOffering::getId).toList();
        Map<Long, List<CourseOfferingClass>> linksByOffering = courseOfferingClassRepository
                .findByCourseOfferingIdInOrderByIdAsc(offeringIds).stream()
                .collect(Collectors.groupingBy(CourseOfferingClass::getCourseOfferingId,
                        LinkedHashMap::new, Collectors.toList()));
        List<Long> classIds = linksByOffering.values().stream()
                .flatMap(List::stream).map(CourseOfferingClass::getClassId).distinct().toList();
        Map<Long, Clazz> clazzMap = classIds.isEmpty() ? Map.of()
                : clazzRepository.findAllById(classIds).stream()
                        .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));

        Map<Long, Course> courseMap = resolveCourseMap(offerings);
        Map<Long, Teacher> teacherMap = resolveTeacherMap(offerings);

        return offerings.stream()
                .map(offering -> {
                    List<Clazz> classes = toOrderedClasses(
                            offering.getId(), linksByOffering.get(offering.getId()), clazzMap);
                    Course course = courseMap.get(offering.getCourseId());
                    Teacher teacher = teacherMap.get(offering.getTeacherId());
                    return CourseOfferingOptionResponse.builder()
                            .id(offering.getId())
                            .courseId(offering.getCourseId())
                            .courseCode(course == null ? null : course.getCourseCode())
                            .courseName(course == null ? null : course.getCourseName())
                            .courseType(course == null ? null : course.getCourseType())
                            .teacherId(offering.getTeacherId())
                            .teacherName(teacher == null ? null : teacher.getName())
                            .semester(offering.getSemester())
                            .weeklySessions(offering.getWeeklySessions())
                            .durationSlots(offering.getDurationSlots())
                            .isLabCourse(offering.getIsLabCourse() != null && offering.getIsLabCourse() == 1)
                            .classIds(classes.stream().map(Clazz::getId).toList())
                            .classNames(classes.stream().map(Clazz::getClassName).toList())
                            .build();
                })
                .toList();
    }

    // ---------- 任务完整数据装配(3C-4: 为后续 SchedulingProblem 组装提供稳定入口) ----------

    /**
     * 从单个排课任务装配出后续算法所需的全部数据 (3C-4)。
     *
     * 返回: 任务本体(含 SA 参数与软约束权重) + 任务纳入的 CourseOffering(按纳入顺序,
     * 每个 offering 附带 course/teacher 冗余与覆盖班级集合) + 可用教室池 + 系统全量时间段。
     *
     * 引用完整性(3C-4): 对本阶段三条链(Task→Offering、Task→Classroom、Offering→Class)做读取侧
     * 显式校验 —— 若范围引用的主数据缺失(理论上被既有删除保护阻止, 仅绕过保护直接改库会出现),
     * 立即抛业务异常拒绝装配, 避免算法在残缺范围上静默运行;
     * offering 的 course/teacher 冗余允许为 null(供组装阶段报友好错误, 本阶段不做删除侧变更)。
     *
     * 注意: 本方法不构造 SchedulingUnit/SchedulingProblem 等算法结构, 只提供稳定数据视图;
     *       hardConstraintWeight 为 null 的 W_hard 计算时机仍保留给排课执行阶段。
     */
    @Transactional(readOnly = true)
    public SchedulingTaskData getSchedulingTaskData(Long taskId) {
        SchedulingTask task = getEntity(taskId);

        // ---- Task → Offering(关联顺序稳定) ----
        List<SchedulingTaskCourse> offeringLinks = schedulingTaskCourseRepository
                .findBySchedulingTaskIdOrderByIdAsc(taskId);
        List<Long> offeringIds = offeringLinks.stream()
                .map(SchedulingTaskCourse::getCourseOfferingId).toList();
        Map<Long, CourseOffering> offeringMap = offeringIds.isEmpty() ? Map.of()
                : courseOfferingRepository.findAllById(offeringIds).stream()
                        .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a));
        for (Long offeringId : offeringIds) {
            if (!offeringMap.containsKey(offeringId)) {
                throw new BusinessException(400,
                        "任务数据不一致: 任务引用的开课实例不存在, taskId=" + taskId + ", offeringId=" + offeringId);
            }
        }

        // ---- Offering → Class(批量取回, 避免 N+1) ----
        Map<Long, List<CourseOfferingClass>> classLinksByOffering = offeringIds.isEmpty() ? Map.of()
                : courseOfferingClassRepository.findByCourseOfferingIdInOrderByIdAsc(offeringIds).stream()
                        .collect(Collectors.groupingBy(CourseOfferingClass::getCourseOfferingId,
                                LinkedHashMap::new, Collectors.toList()));
        Map<Long, Clazz> clazzMap = resolveClazzMap(classLinksByOffering, taskId);

        // ---- offering 的 course/teacher 冗余(批量取回; 缺失容忍为 null) ----
        Map<Long, Course> courseMap = resolveCourseMap(offeringMap.values());
        Map<Long, Teacher> teacherMap = resolveTeacherMap(offeringMap.values());

        List<CourseOfferingData> offeringDataList = offeringLinks.stream()
                .map(link -> offeringMap.get(link.getCourseOfferingId()))
                .filter(Objects::nonNull)
                .map(offering -> CourseOfferingData.builder()
                        .offering(offering)
                        .course(courseMap.get(offering.getCourseId()))
                        .teacher(teacherMap.get(offering.getTeacherId()))
                        .classes(toOrderedClasses(offering.getId(), classLinksByOffering.get(offering.getId()), clazzMap))
                        .build())
                .toList();

        // ---- Task → Classroom ----
        List<SchedulingTaskClassroom> classroomLinks = schedulingTaskClassroomRepository
                .findBySchedulingTaskIdOrderByIdAsc(taskId);
        List<Long> classroomIds = classroomLinks.stream()
                .map(SchedulingTaskClassroom::getClassroomId).toList();
        Map<Long, Classroom> classroomMap = classroomIds.isEmpty() ? Map.of()
                : classroomRepository.findAllById(classroomIds).stream()
                        .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        for (Long classroomId : classroomIds) {
            if (!classroomMap.containsKey(classroomId)) {
                throw new BusinessException(400,
                        "任务数据不一致: 任务引用的教室不存在, taskId=" + taskId + ", classroomId=" + classroomId);
            }
        }
        List<Classroom> classrooms = classroomLinks.stream()
                .map(link -> classroomMap.get(link.getClassroomId()))
                .filter(Objects::nonNull)
                .toList();

        // ---- 全量时间段(稳定顺序) ----
        List<TimeSlot> timeSlots = timeSlotRepository.findAllByOrderByDayOfWeekAscPeriodAsc();

        return SchedulingTaskData.builder()
                .task(task)
                .offerings(offeringDataList)
                .classrooms(classrooms)
                .timeSlots(timeSlots)
                .build();
    }

    // ---------- 内部方法 ----------

    private SchedulingTask getEntity(Long id) {
        return schedulingTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "排课任务不存在: id=" + id));
    }

    /**
     * 状态保护: 仅 PENDING 允许写操作(RUNNING/COMPLETED/FAILED 一律拒绝,
     * FAILED 的"重新执行"语义留排课执行阶段专门设计)。
     */
    private SchedulingTask requirePendingForWrite(SchedulingTask task, String action) {
        if (!Constants.TASK_PENDING.equals(task.getStatus())) {
            throw new BusinessException(400,
                    "仅 PENDING 状态的任务允许" + action + ", 当前状态: " + task.getStatus());
        }
        return task;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /** 批量取班级实体(id→实体); links 为空返回空 map */
    private Map<Long, Clazz> resolveClazzMap(Map<Long, List<CourseOfferingClass>> classLinksByOffering, Long taskId) {
        List<Long> classIds = classLinksByOffering.values().stream()
                .flatMap(List::stream)
                .map(CourseOfferingClass::getClassId)
                .distinct()
                .toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Clazz> map = clazzRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
        for (Long classId : classIds) {
            if (!map.containsKey(classId)) {
                throw new BusinessException(400,
                        "任务数据不一致: 任务关联的班级不存在, taskId=" + taskId + ", classId=" + classId);
            }
        }
        return map;
    }

    /** 按关联顺序把某 offering 的班级行映射为班级实体(异常缺失则跳过, 保持顺序稳定) */
    private List<Clazz> toOrderedClasses(Long offeringId,
                                         List<CourseOfferingClass> links,
                                         Map<Long, Clazz> clazzMap) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .map(link -> clazzMap.get(link.getClassId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 批量取 offering 引用到的课程(id→实体; 缺失容忍为 null, 由组装阶段报友好错误) */
    private Map<Long, Course> resolveCourseMap(Collection<CourseOffering> offerings) {
        List<Long> courseIds = offerings.stream()
                .map(CourseOffering::getCourseId).filter(Objects::nonNull).distinct().toList();
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
    }

    /** 批量取 offering 引用到的教师(id→实体; 缺失容忍为 null, 由组装阶段报友好错误) */
    private Map<Long, Teacher> resolveTeacherMap(Collection<CourseOffering> offerings) {
        List<Long> teacherIds = offerings.stream()
                .map(CourseOffering::getTeacherId).filter(Objects::nonNull).distinct().toList();
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private double defaultDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    /** 按关联顺序把 offering 行映射为响应(批量取 course/teacher 冗余名, 资源缺失跳过保持顺序稳定) */
    private List<CourseOfferingResponse> toOfferingResponses(List<SchedulingTaskCourse> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> offeringIds = links.stream().map(SchedulingTaskCourse::getCourseOfferingId).toList();
        Map<Long, CourseOffering> offeringMap = courseOfferingRepository.findAllById(offeringIds).stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a));
        List<Long> courseIds = offeringMap.values().stream()
                .map(CourseOffering::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = offeringMap.values().stream()
                .map(CourseOffering::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Course> courseMap = courseIds.isEmpty() ? Map.of()
                : courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of()
                : teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
        return links.stream()
                .map(link -> offeringMap.get(link.getCourseOfferingId()))
                .filter(Objects::nonNull)
                .map(offering -> CourseOfferingResponse.from(offering,
                        courseMap.get(offering.getCourseId()), teacherMap.get(offering.getTeacherId())))
                .toList();
    }

    /** 按关联顺序把教室行映射为响应(教室理论上必存在; 异常缺失则跳过) */
    private List<ClassroomResponse> toClassroomResponses(List<SchedulingTaskClassroom> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> classroomIds = links.stream().map(SchedulingTaskClassroom::getClassroomId).toList();
        Map<Long, Classroom> classroomMap = classroomRepository.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));
        return links.stream()
                .map(link -> classroomMap.get(link.getClassroomId()))
                .filter(Objects::nonNull)
                .map(ClassroomResponse::from)
                .toList();
    }
}
