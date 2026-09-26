package com.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 排课结果统计实体 - 对应 scheduling_result 表 (V2.2 §19)
 *
 * 一次任务一次成功的算法运行产生一行统计(与 task 1:1, task_id 唯一)。
 * 由 SchedulingExecutionStore 在"保存结果 + 置任务 COMPLETED"的同一短事务内写入,
 * 若该事务回滚则本表不残留任何行(保证不产生半套结果)。
 *
 * 字段与 schema.sql scheduling_result 逐列一致:
 *   best_fitness / hard_violation_count / soft_violation_count /
 *   iteration_count / total_neighbor_evals / execution_time_ms /
 *   initial_fitness / iteration_history(JSON TEXT) / finish_time
 */
@Entity
@Table(name = "scheduling_result")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 排课任务ID(scheduling_task.id, 唯一) */
    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    /** 最终能量值(最优解) */
    @Column(name = "best_fitness", nullable = false)
    private Double bestFitness;

    /** 硬约束违反总数 */
    @Column(name = "hard_violation_count", nullable = false)
    private Integer hardViolationCount;

    /** 软约束违反总数(原始量四舍五入, schema 列为 INT) */
    @Column(name = "soft_violation_count", nullable = false)
    private Integer softViolationCount;

    /** 实际温度迭代次数 */
    @Column(name = "iteration_count", nullable = false)
    private Integer iterationCount;

    /** 总邻域评价次数 */
    @Column(name = "total_neighbor_evals", nullable = false)
    private Long totalNeighborEvals;

    /** 运行时间(毫秒, 仅算法执行段) */
    @Column(name = "execution_time_ms", nullable = false)
    private Long executionTimeMs;

    /** 初始解(贪心)能量 */
    @Column(name = "initial_fitness")
    private Double initialFitness;

    /** JSON: [{tempIteration, temperature, currentEnergy, bestEnergy, hardViolation, softPenalty}] */
    @Column(name = "iteration_history", columnDefinition = "TEXT")
    private String iterationHistory;

    /** 完成时间 */
    @Column(name = "finish_time", nullable = false)
    private LocalDateTime finishTime;
}
