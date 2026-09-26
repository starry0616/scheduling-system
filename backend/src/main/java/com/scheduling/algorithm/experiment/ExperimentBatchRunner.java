package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.InitialStrategy;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SimulatedAnnealingParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 实验批量运行器(阶段8.2-A)。
 *
 * <p>对 seeds 列表逐个调用 ExperimentRunner.run, 返回与 seeds 一一对应的
 * List&lt;ExperimentResult&gt;。
 *
 * <p>seed 隔离保证:
 * <ul>
 *   <li>每次实验独立调用 SimulatedAnnealing.run(..., seed, ...), 内部各自
 *       new Random(seed), 状态不跨实验共享;</li>
 *   <li>运行顺序不影响任何单次结果: batch.get(i) 等价于单独
 *       run(seed[i])(除自动 experimentId 与 runtimeMs)。</li>
 * </ul>
 */
public final class ExperimentBatchRunner {

    private ExperimentBatchRunner() {
    }

    /** 返回顺序与输入 seeds 完全一致 */
    public static List<ExperimentResult> run(SchedulingProblem problem,
                                             SimulatedAnnealingParams params,
                                             InitialStrategy strategy,
                                             List<Long> seeds) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(seeds, "seeds");
        List<Long> seedList = List.copyOf(seeds);

        List<ExperimentResult> results = new ArrayList<>(seedList.size());
        for (Long seed : seedList) {
            if (seed == null) {
                throw new IllegalArgumentException("seeds 不能包含 null");
            }
            results.add(ExperimentRunner.run(problem, params, seed, strategy));
        }
        return List.copyOf(results);
    }
}
