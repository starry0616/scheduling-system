package com.scheduling.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduling.algorithm.AnnealingResult;
import com.scheduling.algorithm.IterationPoint;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SimulatedAnnealing;
import com.scheduling.algorithm.SimulatedAnnealingParams;
import com.scheduling.common.Constants;
import com.scheduling.dto.SchedulingRunResponse;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.ResourceUnavailability;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.TeacherPreference;
import com.scheduling.repository.ResourceUnavailabilityRepository;
import com.scheduling.repository.TeacherPreferenceRepository;
import com.scheduling.service.SchedulingTaskService;
import com.scheduling.vo.CourseOfferingData;
import com.scheduling.vo.SchedulingTaskData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排课执行服务(阶段六: "SchedulingTask → 数据库数据 → SchedulingProblem →
 * SimulatedAnnealing → SchedulingSolution → 结果落库" 的完整闭环)。
 *
 * <p>事务边界(本方法本身不持有 @Transactional):
 * <pre>
 *   1. [事务 A, 短]  store.claimRunning    PENDING → RUNNING(原子条件更新)
 *   2. [事务外]      读任务数据视图 + 加载资源偏好/不可用 → 组装 Problem → 运行模拟退火
 *                    (算法运行几秒~几分钟期间不占用任何数据库事务/连接)
 *   3. [事务 B, 短]  store.persistCompleted RUNNING → COMPLETED + 结果/条目/关联一次性落库
 *   4. [事务 C, 短]  不可行/系统异常时 store.markFailed RUNNING → FAILED(单独短事务)
 * </pre>
 *
 * <p>状态机(严格):
 * <pre>
 *   PENDING → RUNNING → COMPLETED(成功)
 *   PENDING → RUNNING → FAILED(数据不可行 / 系统异常)
 *   COMPLETED / FAILED / RUNNING 均拒绝再次执行(claim 的原子条件更新保证)
 * </pre>
 *
 * <p>禁止重执行的当前阶段不提供重试。保存结果的事务 B 失败会整体回滚,
 * 绝不残留"结果已写、任务仍 RUNNING"的半套数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingExecutionService {

    private final SchedulingTaskService taskService;
    private final SchedulingExecutionStore store;
    private final SchedulingProblemAssembler assembler;
    private final TeacherPreferenceRepository teacherPreferenceRepository;
    private final ResourceUnavailabilityRepository resourceUnavailabilityRepository;
    private final ObjectMapper objectMapper;

    /** 执行一次排课(同步)。任务状态见类注释状态机。 */
    public SchedulingRunResponse run(Long taskId) {
        // 1. 事务 A: 认领任务(非 PENDING 会在此直接抛 400/404, 不进入后续任何状态迁移)
        store.claimRunning(taskId);

        try {
            // 2. 事务外: 加载数据并组装问题模型
            SchedulingTaskData data = taskService.getSchedulingTaskData(taskId);
            Map<Long, Map<Long, Integer>> preferences = loadTeacherPreferences(data);
            Availability availability = loadResourceUnavailability(data);
            SchedulingProblem problem = assembler.assemble(
                    data, preferences,
                    availability.teacherBlocked,
                    availability.classBlocked,
                    availability.classroomBlocked);

            // 3. 事务外: 运行模拟退火(唯一耗时区段, 不占用数据库连接)
            SimulatedAnnealingParams params = toParams(data.getTask());
            Long seed = data.getTask().getRandomSeed();
            long algorithmStart = System.nanoTime();
            AnnealingResult result = SimulatedAnnealing.run(problem, params, seed);
            long runtimeMs = (System.nanoTime() - algorithmStart) / 1_000_000L;

            // 4. 事务 B: 结果一次性落库 + 任务置 COMPLETED(含 W_hard 回填)
            String historyJson = toHistoryJson(result.history());
            Long resultId = store.persistCompleted(taskId, problem, result, runtimeMs, historyJson);

            return toResponse(taskId, problem, result, resultId, runtimeMs, seed);
        } catch (InfeasibleDataException e) {
            // 数据不可行: 先落 FAILED(单独短事务), 再把不可行原因抛给调用方
            store.markFailed(taskId, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            // 系统异常/算法参数异常等: 先落 FAILED, 再原样抛出
            log.error("排课执行失败: taskId={}", taskId, e);
            store.markFailed(taskId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // 数据转换(Repository 结果 → 算法层纯数据; JPA 实体不进算法核心)
    // ------------------------------------------------------------------

    /** 教师偏好: teacherId → (timeSlotId → level), 无记录=0(由算法层 getOrDefault 兜底) */
    private Map<Long, Map<Long, Integer>> loadTeacherPreferences(SchedulingTaskData data) {
        Map<Long, Map<Long, Integer>> preferences = new HashMap<>();
        List<TeacherPreference> rows = teacherPreferenceRepository.findByTeacherIdIn(
                collectTeacherIds(data));
        for (TeacherPreference row : rows) {
            preferences.computeIfAbsent(row.getTeacherId(), k -> new HashMap<>())
                    .put(row.getTimeSlotId(),
                            row.getPreferenceLevel() == null ? 0 : row.getPreferenceLevel());
        }
        return preferences;
    }

    /** 资源不可用: 按 schema 无 task_id 语义, 只按任务实际涉及的资源集合分批查询并归类 */
    private Availability loadResourceUnavailability(SchedulingTaskData data) {
        Set<Long> teacherIds = collectTeacherIds(data);
        Set<Long> classIds = new LinkedHashSet<>();
        Set<Long> classroomIds = new LinkedHashSet<>();
        for (CourseOfferingData od : data.getOfferings()) {
            for (Clazz clazz : od.getClasses()) {
                classIds.add(clazz.getId());
            }
        }
        if (data.getClassrooms() != null) {
            for (Classroom classroom : data.getClassrooms()) {
                classroomIds.add(classroom.getId());
            }
        }

        List<ResourceUnavailability> rows = new ArrayList<>();
        if (!teacherIds.isEmpty()) {
            rows.addAll(resourceUnavailabilityRepository.findByResourceTypeAndResourceIdIn(
                    Constants.RESOURCE_TEACHER, teacherIds));
        }
        if (!classIds.isEmpty()) {
            rows.addAll(resourceUnavailabilityRepository.findByResourceTypeAndResourceIdIn(
                    Constants.RESOURCE_CLASS, classIds));
        }
        if (!classroomIds.isEmpty()) {
            rows.addAll(resourceUnavailabilityRepository.findByResourceTypeAndResourceIdIn(
                    Constants.RESOURCE_CLASSROOM, classroomIds));
        }

        Map<Long, Set<Long>> teacherBlocked = new HashMap<>();
        Map<Long, Set<Long>> classBlocked = new HashMap<>();
        Map<Long, Set<Long>> classroomBlocked = new HashMap<>();
        for (ResourceUnavailability row : rows) {
            Map<Long, Set<Long>> target;
            if (Constants.RESOURCE_TEACHER.equals(row.getResourceType())) {
                target = teacherBlocked;
            } else if (Constants.RESOURCE_CLASS.equals(row.getResourceType())) {
                target = classBlocked;
            } else {
                target = classroomBlocked;
            }
            target.computeIfAbsent(row.getResourceId(), k -> new HashSet<>())
                    .add(row.getTimeSlotId());
        }
        return new Availability(teacherBlocked, classBlocked, classroomBlocked);
    }

    private Set<Long> collectTeacherIds(SchedulingTaskData data) {
        Set<Long> teacherIds = new LinkedHashSet<>();
        for (CourseOfferingData od : data.getOfferings()) {
            CourseOffering offering = od.getOffering();
            if (offering != null) {
                teacherIds.add(offering.getTeacherId());
            }
        }
        return teacherIds;
    }

    /** 任务实体 SA 参数 → 算法纯参数(空值回落系统默认) */
    private SimulatedAnnealingParams toParams(SchedulingTask task) {
        return new SimulatedAnnealingParams(
                doubleOr(task.getMaxInitialTemp(), Constants.DEFAULT_MAX_INITIAL_TEMP),
                doubleOr(task.getMinInitialTemp(), Constants.DEFAULT_MIN_INITIAL_TEMP),
                doubleOr(task.getMinTemp(), Constants.DEFAULT_MIN_TEMP),
                doubleOr(task.getCoolingRate(), Constants.DEFAULT_COOLING_RATE),
                intOr(task.getMaxTempIterations(), Constants.DEFAULT_MAX_TEMP_ITERATIONS),
                intOr(task.getNeighborsPerTemp(), Constants.DEFAULT_NEIGHBORS_PER_TEMP),
                intOr(task.getMaxRepairAttempts(), Constants.DEFAULT_MAX_REPAIR_ATTEMPTS));
    }

    private double doubleOr(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private int intOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String toHistoryJson(List<IterationPoint> history) {
        try {
            return objectMapper.writeValueAsString(history == null ? List.of() : history);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("排课迭代历史序列化失败", e);
        }
    }

    private SchedulingRunResponse toResponse(Long taskId, SchedulingProblem problem,
                                             AnnealingResult result, Long resultId,
                                             long runtimeMs, Long seed) {
        boolean feasible = result.isFeasible();
        return SchedulingRunResponse.builder()
                .taskId(taskId)
                .resultId(resultId)
                .status(Constants.TASK_COMPLETED)
                .outcome(feasible ? "FEASIBLE" : "BEST_EFFORT")
                .feasible(feasible)
                .hardViolation(result.bestHardViolation())
                .softPenalty(result.bestSoftPenalty())
                .energy(result.bestEnergy())
                .runtimeMs(runtimeMs)
                .iterations(result.tempIterations())
                .seed(seed)
                .hardConstraintWeight(problem.getHardWeightAsLong())
                .build();
    }

    /** 资源不可用时间归类结果(纯数据, 供组装器消费) */
    private record Availability(Map<Long, Set<Long>> teacherBlocked,
                                Map<Long, Set<Long>> classBlocked,
                                Map<Long, Set<Long>> classroomBlocked) {
    }
}
