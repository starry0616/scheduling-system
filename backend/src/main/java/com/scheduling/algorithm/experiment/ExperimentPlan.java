package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.InitialStrategy;
import com.scheduling.algorithm.SimulatedAnnealingParams;
import com.scheduling.common.Constants;

import java.util.List;
import java.util.Objects;

/**
 * 一个实验组(Experiment Plan)的声明式描述(阶段8.3, V2.2 §24.4)。
 *
 * <p>一个 plan 定义一次"单因素"对照单元:
 * <pre>
 *   planId            : 组标识(如 baseline-SMALL / cooling-0.85), 矩阵输出可读;
 *   datasetSize       : SMALL / MEDIUM / LARGE;
 *   datasetSeed       : 数据集随机种子(同一 seed ⟹ 数据集逐位一致, 见 DatasetFactory);
 *   initialStrategy   : GREEDY / RANDOM;
 *   params            : 该组使用的完整 SA 参数(默认或单因素变化后的值);
 *   experimentSeeds   : 本组内逐次实验的独立随机种子(每次实验 new Random(seed), 互不共享状态)。
 * </pre>
 *
 * <p>实验组内所有 experiment seed 共享同一份 dataset(datasetSize + datasetSeed 唯一),
 * 因此组内差异只来自实验 seed 的随机搜索路径, 便于组内统计与组间对照。
 */
public record ExperimentPlan(
        String planId,
        ExperimentDatasetSize datasetSize,
        long datasetSeed,
        InitialStrategy initialStrategy,
        SimulatedAnnealingParams params,
        List<Long> experimentSeeds) {

    /** 标准实验 seed 集(20260901 ~ 20260910, 与数据集说明日对齐, 便于论文表述) */
    public static final List<Long> STANDARD_SEEDS = List.of(
            20260901L, 20260902L, 20260903L, 20260904L, 20260905L,
            20260906L, 20260907L, 20260908L, 20260909L, 20260910L);

    public ExperimentPlan {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId 不能为空");
        }
        Objects.requireNonNull(datasetSize, "datasetSize");
        Objects.requireNonNull(initialStrategy, "initialStrategy");
        Objects.requireNonNull(params, "params");
        if (experimentSeeds == null || experimentSeeds.isEmpty()) {
            throw new IllegalArgumentException("experimentSeeds 不能为空");
        }
        List<Long> copied = List.copyOf(experimentSeeds);
        for (Long seed : copied) {
            if (seed == null) {
                throw new IllegalArgumentException("experimentSeeds 不能包含 null");
            }
        }
        experimentSeeds = copied;
    }

    // ---------- 默认参数(与生产 Constants.DEFAULT_* 保持单一事实来源) ----------

    /** 当前系统默认 SA 参数(即基线/未改变参数的对照) */
    public static SimulatedAnnealingParams defaultParams() {
        return new SimulatedAnnealingParams(
                Constants.DEFAULT_MAX_INITIAL_TEMP,
                Constants.DEFAULT_MIN_INITIAL_TEMP,
                Constants.DEFAULT_MIN_TEMP,
                Constants.DEFAULT_COOLING_RATE,
                Constants.DEFAULT_MAX_TEMP_ITERATIONS,
                Constants.DEFAULT_NEIGHBORS_PER_TEMP,
                Constants.DEFAULT_MAX_REPAIR_ATTEMPTS);
    }

    /**
     * 在默认参数基础上只覆盖 coolingRate 与 neighborsPerTemp 的变体
     * (供单因素实验构造; 其余参数保持默认)。
     */
    public static SimulatedAnnealingParams defaultParams(double coolingRate, int neighborsPerTemp) {
        SimulatedAnnealingParams base = defaultParams();
        return new SimulatedAnnealingParams(
                base.maxInitialTemp(),
                base.minInitialTemp(),
                base.minTemp(),
                coolingRate,
                base.maxTempIterations(),
                neighborsPerTemp,
                base.maxRepairAttempts());
    }
}
