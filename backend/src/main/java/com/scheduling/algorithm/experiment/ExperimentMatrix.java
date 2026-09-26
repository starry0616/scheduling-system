package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.InitialStrategy;
import com.scheduling.algorithm.SimulatedAnnealingParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准实验矩阵定义(阶段8.3, V2.2 §24.4)。
 *
 * <p>按单因素对照组织四个实验族:
 * <pre>
 *   baseline             : SMALL/MEDIUM/LARGE × GREEDY × 10 seeds(默认 SA 参数);
 *   initial-strategy     : 三规模 × {GREEDY, RANDOM} × 10 seeds(其余同默认);
 *   cooling-rate         : MEDIUM × GREEDY × {0.85,0.90,0.95,0.99,0.995} × 10 seeds;
 *   neighbors-per-temp   : MEDIUM × GREEDY × {5,10,20,40,80} × 10 seeds(其余同默认)。
 * </pre>
 * 每组内 datasetSeed 固定为 {@link #STANDARD_DATASET_SEED}, experiment seeds 用
 * {@link ExperimentPlan#STANDARD_SEEDS}(20260901..20260910)。
 *
 * <p>说明: 不改变未研究参数; T0 由初始解能量推导, 相关实验只报告观测结果,
 * 不宣称严格控温/迭代预算(见技术方案实验设计限制)。
 */
public final class ExperimentMatrix {

    /** 标准实验统一数据集 seed(各实验族、各规模共用, 保证数据集可比) */
    public static final long STANDARD_DATASET_SEED = 20260901L;

    public static final List<Double> COOLING_RATES = List.of(0.85, 0.90, 0.95, 0.99, 0.995);
    public static final List<Integer> NEIGHBORS_PER_TEMP_VALUES = List.of(5, 10, 20, 40, 80);

    /** 标准实验族名(顺序即执行/输出顺序) */
    public static final List<String> FAMILY_NAMES = List.of(
            "baseline",
            "initial-strategy",
            "cooling-rate",
            "neighbors-per-temp");

    private ExperimentMatrix() {
    }

    /** 全部标准实验族 → 每组新建的计划列表(可重复调用, 不改共享常量) */
    public static Map<String, List<ExperimentPlan>> families() {
        Map<String, List<ExperimentPlan>> map = new LinkedHashMap<>();
        map.put(FAMILY_NAMES.get(0), baselinePlans());
        map.put(FAMILY_NAMES.get(1), initialStrategyPlans());
        map.put(FAMILY_NAMES.get(2), coolingRatePlans());
        map.put(FAMILY_NAMES.get(3), neighborsPerTempPlans());
        return map;
    }

    // ---------- 各实验族 ----------

    /** 基线: 三规模 × GREEDY × 默认 SA 参数 × 10 seeds */
    public static List<ExperimentPlan> baselinePlans() {
        List<ExperimentPlan> plans = new ArrayList<>();
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            plans.add(new ExperimentPlan(
                    "baseline-" + size.name(),
                    size,
                    STANDARD_DATASET_SEED,
                    InitialStrategy.GREEDY,
                    ExperimentPlan.defaultParams(),
                    ExperimentPlan.STANDARD_SEEDS));
        }
        return plans;
    }

    /** 初始解策略: 三规模 × {GREEDY, RANDOM} × 默认 SA 参数 × 10 seeds */
    public static List<ExperimentPlan> initialStrategyPlans() {
        List<ExperimentPlan> plans = new ArrayList<>();
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            for (InitialStrategy strategy : InitialStrategy.values()) {
                plans.add(new ExperimentPlan(
                        "initial-strategy-" + size.name() + "-" + strategy.name(),
                        size,
                        STANDARD_DATASET_SEED,
                        strategy,
                        ExperimentPlan.defaultParams(),
                        ExperimentPlan.STANDARD_SEEDS));
            }
        }
        return plans;
    }

    /** 冷却速率: MEDIUM × GREEDY × 5 档 coolingRate × 10 seeds(neighborsPerTemp 等保持默认) */
    public static List<ExperimentPlan> coolingRatePlans() {
        List<ExperimentPlan> plans = new ArrayList<>();
        for (double rate : COOLING_RATES) {
            SimulatedAnnealingParams params =
                    ExperimentPlan.defaultParams(rate, ExperimentPlan.defaultParams().neighborsPerTemp());
            plans.add(new ExperimentPlan(
                    "cooling-" + rate,
                    ExperimentDatasetSize.MEDIUM,
                    STANDARD_DATASET_SEED,
                    InitialStrategy.GREEDY,
                    params,
                    ExperimentPlan.STANDARD_SEEDS));
        }
        return plans;
    }

    /** 每温度邻域评价: MEDIUM × GREEDY × 5 档 neighborsPerTemp × 10 seeds(coolingRate 等保持默认) */
    public static List<ExperimentPlan> neighborsPerTempPlans() {
        List<ExperimentPlan> plans = new ArrayList<>();
        SimulatedAnnealingParams base = ExperimentPlan.defaultParams();
        for (int neighbors : NEIGHBORS_PER_TEMP_VALUES) {
            SimulatedAnnealingParams params = new SimulatedAnnealingParams(
                    base.maxInitialTemp(),
                    base.minInitialTemp(),
                    base.minTemp(),
                    base.coolingRate(),
                    base.maxTempIterations(),
                    neighbors,
                    base.maxRepairAttempts());
            plans.add(new ExperimentPlan(
                    "neighbors-" + neighbors,
                    ExperimentDatasetSize.MEDIUM,
                    STANDARD_DATASET_SEED,
                    InitialStrategy.GREEDY,
                    params,
                    ExperimentPlan.STANDARD_SEEDS));
        }
        return plans;
    }
}
