package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.SchedulingProblem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实验计划批量执行器(阶段8.3)。
 *
 * <p>职责: 顺序执行一组 ExperimentPlan, 返回与输入一一对应的
 * List&lt;ExperimentPlanResult&gt;。
 *
 * <p>保证:
 * <ul>
 *   <li>同一 (datasetSize, datasetSeed) 的数据集只在一次 runAll 中生成一次
 *       (按 (size, seed) 缓存复用), 且 DatasetFactory 本身按 seed 完全确定;</li>
 *   <li>每次 experiment 用独立 experiment seed 独立调用 ExperimentRunner
 *       (内部 new Random(seed)), 状态不跨实验共享; 运行顺序不影响任何单次结果;</li>
 *   <li>experimentId 由 planId + seed 确定, 使矩阵 CSV 跨次执行可重复
 *       (runtimeMs 除外);</li>
 *   <li>不吞异常: 任一实验抛出 RuntimeException 直接向上传播(无静默跳过),
 *       不会留下半成品结果; 调用方可在 plan 粒度捕获后继续, 各 plan 之间本就互不影响。</li>
 * </ul>
 *
 * <p>注意: 数据集结构性可行由 DatasetFactory 配额设计保证(阶段8.2-B);
 * 若个别 seed 出现无候选数据, SchedulingProblem 构造会抛 IllegalArgumentException
 * (即数据不可行信号), 本执行器原样传播并记录为失败, 不跳过。
 */
public final class ExperimentPlanRunner {

    private record DatasetKey(ExperimentDatasetSize size, long seed) {
    }

    private ExperimentPlanRunner() {
    }

    /** 运行单个计划(单次调用的独立数据集缓存) */
    public static ExperimentPlanResult run(ExperimentPlan plan) {
        return runAll(List.of(Objects.requireNonNull(plan, "plan"))).get(0);
    }

    /** 顺序运行一组计划; 返回顺序与输入 plans 完全一致 */
    public static List<ExperimentPlanResult> runAll(List<ExperimentPlan> plans) {
        Objects.requireNonNull(plans, "plans");
        List<ExperimentPlan> planList = List.copyOf(plans);

        Map<DatasetKey, SchedulingProblem> datasetCache = new LinkedHashMap<>();
        List<ExperimentPlanResult> out = new ArrayList<>(planList.size());

        for (ExperimentPlan plan : planList) {
            SchedulingProblem problem = datasetCache.computeIfAbsent(
                    new DatasetKey(plan.datasetSize(), plan.datasetSeed()),
                    key -> DatasetFactory.create(key.size(), key.seed()));

            List<ExperimentResult> results = new ArrayList<>(plan.experimentSeeds().size());
            for (long seed : plan.experimentSeeds()) {
                String experimentId = plan.planId() + "-s" + seed;
                results.add(ExperimentRunner.run(experimentId, problem,
                        plan.params(), seed, plan.initialStrategy()));
            }
            out.add(new ExperimentPlanResult(plan, results));
        }
        return List.copyOf(out);
    }
}
