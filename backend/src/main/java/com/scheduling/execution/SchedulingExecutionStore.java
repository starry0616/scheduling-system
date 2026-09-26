package com.scheduling.execution;

import com.scheduling.algorithm.AnnealingResult;
import com.scheduling.algorithm.CandidateAssignment;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SchedulingSolution;
import com.scheduling.algorithm.SchedulingUnit;
import com.scheduling.common.Constants;
import com.scheduling.entity.ScheduleEntry;
import com.scheduling.entity.ScheduleEntryClass;
import com.scheduling.entity.SchedulingResult;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ScheduleEntryClassRepository;
import com.scheduling.repository.ScheduleEntryRepository;
import com.scheduling.repository.SchedulingResultRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 排课执行存储服务(阶段六, 事务边界核心)。
 *
 * <p>职责: 把"长任务 - 数据库事务"彻底拆开 —— 算法执行期间不持有任何数据库事务。
 * 对外仅暴露三个独立的短事务操作:
 * <pre>
 *   [事务 A] claimRunning     PENDING → RUNNING(原子条件更新, 防并发双跑)
 *   [事务外]  算法执行(由 SchedulingExecutionService 负责, 本类不参与)
 *   [事务 B] persistCompleted RUNNING → COMPLETED + 结果/条目/班级关联一次性写入
 *   [事务 C] markFailed       RUNNING → FAILED(不可行/异常时的单独短事务)
 * </pre>
 *
 * <p>事务 B 的原子性保证: SchedulingResult / ScheduleEntry / ScheduleEntryClass /
 * 任务状态更新 全部在一个事务内, 任一步失败整体回滚 —— 绝不留下半套结果,
 * 也绝不会出现"结果已落库但任务状态仍 RUNNING"的孤儿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingExecutionStore {

    private final SchedulingTaskRepository taskRepository;
    private final SchedulingResultRepository resultRepository;
    private final ScheduleEntryRepository entryRepository;
    private final ScheduleEntryClassRepository entryClassRepository;

    /**
     * 事务 A: 认领任务(PENDING → RUNNING)。
     * 采用原子条件更新: 两个请求并发时只有一个能成功迁移, 天然防并发双跑。
     */
    @Transactional
    public SchedulingTask claimRunning(Long taskId) {
        int updated = taskRepository.transitionStatus(taskId, Constants.TASK_PENDING,
                Constants.TASK_RUNNING, null);
        if (updated == 1) {
            return taskRepository.findById(taskId)
                    .orElseThrow(() -> new BusinessException(404, "排课任务不存在, taskId=" + taskId));
        }
        SchedulingTask existing = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "排课任务不存在, taskId=" + taskId));
        throw new BusinessException(400,
                "仅 PENDING(待执行) 状态的任务允许执行排课, 当前状态: " + existing.getStatus()
                        + ", taskId=" + taskId);
    }

    /**
     * 事务 B: 一次性写入 结果统计 + 全部条目 + 全部条目-班级关联, 并将任务置 COMPLETED。
     *
     * @return 新写入的 scheduling_result.id
     */
    @Transactional
    public Long persistCompleted(Long taskId, SchedulingProblem problem, AnnealingResult annealing,
                                 long runtimeMs, String iterationHistoryJson) {
        // 0. 防御性清理: 同一任务的历史结果(本阶段禁止重执行, 正常为空操作;
        //    若因异常遗留残片, 也在此一并清除, 保证同一任务结果关系清晰)
        deleteExistingResultsForTask(taskId);

        // 1. scheduling_result(1:1, task_id 唯一)
        SchedulingResult result = SchedulingResult.builder()
                .taskId(taskId)
                .bestFitness(annealing.bestEnergy())
                .hardViolationCount(annealing.bestHardViolation())
                .softViolationCount((int) Math.round(annealing.bestSoftPenalty()))
                .iterationCount(annealing.tempIterations())
                .totalNeighborEvals(annealing.totalNeighborEvaluations())
                .executionTimeMs(runtimeMs)
                .initialFitness(annealing.initialEnergy())
                .iterationHistory(iterationHistoryJson)
                .finishTime(LocalDateTime.now())
                .build();
        SchedulingResult savedResult = resultRepository.save(result);

        // 2. schedule_entry(一个 Unit = 一行; durationSlots 连续语义在候选内, 只存起始 time_slot)
        SchedulingSolution best = annealing.best();
        int unitCount = problem.getUnitCount();
        List<ScheduleEntry> entries = new ArrayList<>(unitCount);
        for (int i = 0; i < unitCount; i++) {
            SchedulingUnit unit = problem.getUnit(i);
            CandidateAssignment candidate = unit.getCandidates().get(best.getChoice(i));
            entries.add(ScheduleEntry.builder()
                    .taskId(taskId)
                    .courseOfferingId(unit.getCourseOfferingId())
                    .teacherId(unit.getTeacherId())
                    .classroomId(candidate.getClassroomId())
                    .timeSlotId(candidate.getStartTimeSlotId())
                    .unitIndex(unit.getSessionIndex())
                    .build());
        }
        List<ScheduleEntry> savedEntries = entryRepository.saveAll(entries);

        // 3. schedule_entry_class(合班 offering 每班一行)
        List<ScheduleEntryClass> entryClasses = new ArrayList<>();
        for (int i = 0; i < savedEntries.size(); i++) {
            long[] classIds = problem.getUnit(i).getClassIds();
            for (long classId : classIds) {
                entryClasses.add(ScheduleEntryClass.builder()
                        .scheduleEntryId(savedEntries.get(i).getId())
                        .classId(classId)
                        .build());
            }
        }
        entryClassRepository.saveAll(entryClasses);

        // 4. 任务 → COMPLETED + 回填 W_hard(与结果同事务; 若被并发改成非 RUNNING
        //    则回滚, 不产生孤儿结果; 绝不出现"结果已写但权重/状态未完成")
        int updated = taskRepository.transitionStatusWithHardWeight(taskId, Constants.TASK_RUNNING,
                Constants.TASK_COMPLETED, LocalDateTime.now(), problem.getHardWeightAsLong());
        if (updated != 1) {
            throw new IllegalStateException(
                    "任务状态已变化, 无法置为 COMPLETED, taskId=" + taskId + ", 结果已回滚");
        }
        return savedResult.getId();
    }

    /**
     * 事务 C: 任务失败置位(RUNNING → FAILED), 单独短事务。
     * 不可行数据 / 系统异常都会走到这里; 仅当任务当前处于 RUNNING 才迁移(防误伤已完成任务)。
     */
    @Transactional
    public void markFailed(Long taskId, String reason) {
        int updated = taskRepository.transitionStatus(taskId, Constants.TASK_RUNNING,
                Constants.TASK_FAILED, LocalDateTime.now());
        if (updated == 1) {
            log.warn("排课任务执行失败: taskId={}, reason={}", taskId, reason);
        } else {
            log.warn("排课任务执行失败但状态已非 RUNNING, 跳过置 FAILED: taskId={}, reason={}",
                    taskId, reason);
        }
    }

    /** 同一事务内删除某任务全部历史结果(含条目及其班级关联), 保证无孤儿 */
    private void deleteExistingResultsForTask(Long taskId) {
        List<ScheduleEntry> existingEntries = entryRepository.findByTaskIdOrderByIdAsc(taskId);
        if (!existingEntries.isEmpty()) {
            List<Long> entryIds = existingEntries.stream().map(ScheduleEntry::getId).toList();
            entryClassRepository.deleteByScheduleEntryIdIn(entryIds);
            entryRepository.deleteByTaskId(taskId);
        }
        resultRepository.deleteByTaskId(taskId);
    }

    /** 供测试/内部调用确认某任务当前无任何结果行(验证无半套结果) */
    @Transactional(readOnly = true)
    public boolean taskHasAnyResult(Long taskId) {
        return resultRepository.findByTaskId(taskId).isPresent()
                || !entryRepository.findByTaskIdOrderByIdAsc(taskId).isEmpty();
    }
}
