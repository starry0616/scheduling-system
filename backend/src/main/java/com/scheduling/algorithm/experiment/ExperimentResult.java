package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.InitialStrategy;
import com.scheduling.algorithm.IterationPoint;

import java.util.List;

/**
 * 一次实验运行的独立结果 DTO(阶段8.2-A, V2.2 §24.2)。
 *
 * <p>纯 POJO, 不落库(数据库 schema 不允许为实验指标改动)。
 * 一个 ExperimentResult 对应一次 SimulatedAnnealing.run(...) 的编排快照。
 *
 * @param experimentId             实验标识(ExperimentRunner 自动分配或调用方显式指定)
 * @param seed                     本次运行随机种子(策略 + seed 决定可复现性)
 * @param initialStrategy          初始解策略 GREEDY / RANDOM
 * @param initialFitness           初始解能量(initialEnergy)
 * @param initialHardViolation     初始解硬约束违反数
 * @param initialSoftPenalty       初始解软约束原始违反合计(未加权)
 * @param bestFitness              最终最优解能量
 * @param bestHardViolation        最终最优解硬约束违反数
 * @param bestSoftPenalty          最终最优解软约束原始违反合计(未加权)
 * @param tempIterations           实际温度迭代(外循环)次数
 * @param totalNeighborEvaluations 总邻域评价次数
 * @param runtimeMs                运行耗时毫秒(性能指标, 不参与可复现性比较)
 * @param initialTemperature       实际初始温度 T0; 当前 AnnealingResult 未暴露该值,
 *                                 为不改动 SA 核心按 nullable 设计(阶段8.2-A 恒为 null)
 * @param coolingRate              降温系数(来自 params)
 * @param neighborsPerTemp         每温度内循环次数(来自 params)
 * @param maxTempIterations        外循环次数上限(来自 params)
 * @param minTemp                  终止温度(来自 params)
 * @param maxInitialTemp           初始温度上限(来自 params)
 * @param minInitialTemp           初始温度下限(来自 params)
 * @param hardConstraintWeight     硬约束权重(E(S) = W_hard * HardViolation + Σw*S)
 * @param feasible                 是否可行解(bestHardViolation == 0)
 * @param bestAssignment           最终最优 Assignment(choices, 深拷贝快照)
 * @param history                  每次温度迭代的采样序列(可复现性/曲线用; CSV 不导出)
 */
public record ExperimentResult(
        String experimentId,
        long seed,
        InitialStrategy initialStrategy,
        double initialFitness,
        int initialHardViolation,
        double initialSoftPenalty,
        double bestFitness,
        int bestHardViolation,
        double bestSoftPenalty,
        int tempIterations,
        long totalNeighborEvaluations,
        long runtimeMs,
        Double initialTemperature,
        double coolingRate,
        int neighborsPerTemp,
        int maxTempIterations,
        double minTemp,
        double maxInitialTemp,
        double minInitialTemp,
        double hardConstraintWeight,
        boolean feasible,
        int[] bestAssignment,
        List<IterationPoint> history) {

    /**
     * 记录含数组字段(bestAssignment), 默认 equals/toString 对数组为引用比较;
     * 请用 Arrays.equals / 逐字段比较语义, 勿直接把两个 record 当值相等判断。
     */
    public int[] bestAssignment() {
        return bestAssignment.clone();
    }
}
