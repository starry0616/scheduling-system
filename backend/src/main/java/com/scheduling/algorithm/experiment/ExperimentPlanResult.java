package com.scheduling.algorithm.experiment;

import java.util.List;
import java.util.Objects;

/**
 * 一个实验组的执行结果(阶段8.3)。
 *
 * <p>results.size() == plan.experimentSeeds().size(), 且 results 顺序与
 * plan.experimentSeeds() 一一对应(实验顺序不改变随机语义)。
 *
 * @param plan    对应的实验计划
 * @param results 该组全部逐次实验结果(含完整指标, 见 ExperimentResult)
 */
public record ExperimentPlanResult(
        ExperimentPlan plan,
        List<ExperimentResult> results) {

    public ExperimentPlanResult {
        Objects.requireNonNull(plan, "plan");
        if (results == null) {
            throw new IllegalArgumentException("results 不能为 null");
        }
        List<ExperimentResult> copied = List.copyOf(results);
        if (copied.size() != plan.experimentSeeds().size()) {
            throw new IllegalArgumentException(
                    "results 数(" + copied.size() + ") 必须等于 plan.seeds 数("
                            + plan.experimentSeeds().size() + "): " + plan.planId());
        }
        results = copied;
    }
}
