package com.scheduling.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 排课执行结果摘要 (阶段六 POST /scheduling-tasks/{taskId}/run 成功响应)
 *
 * outcome 取值:
 *   FEASIBLE    算法完成且 hardViolation == 0
 *   BEST_EFFORT 算法完成但 hardViolation &gt; 0(绝不冒充 FEASIBLE)
 * (INFEASIBLE_DATA / FAILED 不通过本对象返回, 而是抛业务/系统错误并在 message 中说明原因)
 */
@Data
@Builder
public class SchedulingRunResponse {

    /** 排课任务ID */
    private Long taskId;

    /** 排课结果统计ID(scheduling_result.id) */
    private Long resultId;

    /** 任务状态: 成功执行为 COMPLETED */
    private String status;

    /** 算法结果: FEASIBLE / BEST_EFFORT */
    private String outcome;

    /** 是否可行(hardViolation == 0) */
    private boolean feasible;

    /** 硬约束违反总数 */
    private int hardViolation;

    /** 软约束原始违反合计 */
    private double softPenalty;

    /** 最优解能量 */
    private double energy;

    /** 算法运行时间(毫秒) */
    private long runtimeMs;

    /** 实际温度迭代次数 */
    private int iterations;

    /** 实际使用的随机种子(为 null 表示按系统时间自动播种) */
    private Long seed;

    /** 回填到任务的硬约束权重 W_hard = SoftMax + 1 */
    private Long hardConstraintWeight;
}
