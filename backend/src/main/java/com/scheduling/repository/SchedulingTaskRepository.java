package com.scheduling.repository;

import com.scheduling.entity.SchedulingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 排课任务 Repository (第3步第三阶段 3C-3)
 */
@Repository
public interface SchedulingTaskRepository extends JpaRepository<SchedulingTask, Long> {

    /** 全量列表, 新任务在前 */
    List<SchedulingTask> findAllByOrderByIdDesc();

    /**
     * 原子状态迁移(执行阶段专用): 仅在当前状态为 fromStatus 时迁移到 toStatus,
     * 并可选回填 finishTime(置 null 则清空)。用于:
     *   PENDING → RUNNING(认领, 防并发双跑)
     *   RUNNING → COMPLETED / FAILED
     * 返回受影响行数(0 = 状态已被并发变更, 调用方据此给出业务错误)。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SchedulingTask t set t.status = :toStatus, t.finishTime = :finishTime "
            + "where t.id = :id and t.status = :fromStatus")
    int transitionStatus(@Param("id") Long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("finishTime") LocalDateTime finishTime);

    /**
     * 原子状态迁移并回填硬约束权重(完成专用): RUNNING → COMPLETED 的同时写入
     * hardConstraintWeight = W_hard(由组装出的 SchedulingProblem 推导)。
     * 与结果/条目同处一个事务, 保证"结果落库 + 权重回填 + 状态完成"三者原子一致。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SchedulingTask t set t.status = :toStatus, t.finishTime = :finishTime, "
            + "t.hardConstraintWeight = :hardWeight "
            + "where t.id = :id and t.status = :fromStatus")
    int transitionStatusWithHardWeight(@Param("id") Long id,
                                       @Param("fromStatus") String fromStatus,
                                       @Param("toStatus") String toStatus,
                                       @Param("finishTime") LocalDateTime finishTime,
                                       @Param("hardWeight") Long hardWeight);

    /** 按学期精确过滤, 新任务在前 */
    List<SchedulingTask> findBySemesterOrderByIdDesc(String semester);

    /** 按状态精确过滤, 新任务在前 */
    List<SchedulingTask> findByStatusOrderByIdDesc(String status);

    /** 按学期 + 状态精确过滤, 新任务在前 */
    List<SchedulingTask> findBySemesterAndStatusOrderByIdDesc(String semester, String status);
}
