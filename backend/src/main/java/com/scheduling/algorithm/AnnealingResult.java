package com.scheduling.algorithm;

import java.util.List;

/**
 * 一次模拟退火运行的完整结果(纯 POJO)。
 *
 * @param best                    最终最优解(用于落库 schedule_entry)
 * @param bestEnergy              最优解能量
 * @param bestHardViolation       最优解硬约束违反数
 * @param bestSoftPenalty         最优解软约束原始违反合计
 * @param initialEnergy           初始解(贪心)能量
 * @param initialHardViolation    初始解硬约束违反数
 * @param initialSoftPenalty      初始解软约束原始违反合计
 * @param tempIterations          实际外循环(温度迭代)次数
 * @param totalNeighborEvaluations 总邻域评价次数
 * @param history                 每次温度迭代的采样序列(前端适应度曲线用)
 * @param initialStrategy         本次运行使用的初始解策略(阶段8.1, 实验区分依据)
 */
public record AnnealingResult(
        SchedulingSolution best,
        double bestEnergy,
        int bestHardViolation,
        double bestSoftPenalty,
        double initialEnergy,
        int initialHardViolation,
        double initialSoftPenalty,
        int tempIterations,
        long totalNeighborEvaluations,
        List<IterationPoint> history,
        InitialStrategy initialStrategy) {

    /** 是否在温度耗尽前提前结束(例如最优解能量已为 0) */
    public boolean isFeasible() {
        return bestHardViolation == 0;
    }
}
