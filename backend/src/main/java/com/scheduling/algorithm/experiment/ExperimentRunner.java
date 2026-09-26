package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.AnnealingResult;
import com.scheduling.algorithm.InitialStrategy;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SimulatedAnnealing;
import com.scheduling.algorithm.SimulatedAnnealingParams;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实验运行器(阶段8.2-A, V2.2 §24.2)。
 *
 * <p>最小编排器: 接收 SchedulingProblem + 参数 + seed + InitialStrategy,
 * 只调用 SimulatedAnnealing.run(...) 并封装成 ExperimentResult。
 * 不复制 SA / 能量评估 / 候选生成 / 冲突修复 / 随机数, 不改生产业务调用链。
 *
 * <p>可复现语义: 同一 (problem, params, seed, strategy) 运行结果完全一致
 * (runtimeMs 除外); 同一策略下不同 seed 产生不同搜索路径。
 *
 * <p>无任何 Spring / 数据库 / Web 依赖, 可在纯 Java 环境独立运行。
 */
public final class ExperimentRunner {

    private static final AtomicLong EXPERIMENT_SEQ = new AtomicLong(1);

    private ExperimentRunner() {
    }

    /** 自动分配 experimentId 后运行(同进程内唯一) */
    public static ExperimentResult run(SchedulingProblem problem,
                                       SimulatedAnnealingParams params,
                                       long seed,
                                       InitialStrategy strategy) {
        return run(defaultExperimentId(seed), problem, params, seed, strategy);
    }

    /**
     * 显式指定 experimentId 运行(便于对照分组命名、CSV 转义验证等;
     * 运行语义与自动 id 完全一致)。
     */
    public static ExperimentResult run(String experimentId,
                                       SchedulingProblem problem,
                                       SimulatedAnnealingParams params,
                                       long seed,
                                       InitialStrategy strategy) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(strategy, "strategy");
        String id = (experimentId == null || experimentId.isBlank())
                ? defaultExperimentId(seed)
                : experimentId;

        long startNanos = System.nanoTime();
        AnnealingResult a = SimulatedAnnealing.run(problem, params, seed, strategy);
        long runtimeMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);

        return new ExperimentResult(
                id,
                seed,
                strategy,
                a.initialEnergy(),
                a.initialHardViolation(),
                a.initialSoftPenalty(),
                a.bestEnergy(),
                a.bestHardViolation(),
                a.bestSoftPenalty(),
                a.tempIterations(),
                a.totalNeighborEvaluations(),
                runtimeMs,
                null,                                  // 见 ExperimentResult.initialTemperature 说明
                params.coolingRate(),
                params.neighborsPerTemp(),
                params.maxTempIterations(),
                params.minTemp(),
                params.maxInitialTemp(),
                params.minInitialTemp(),
                problem.getHardWeight(),
                a.isFeasible(),
                a.best().toArray(),
                List.copyOf(a.history()));
    }

    private static String defaultExperimentId(long seed) {
        return "exp-" + EXPERIMENT_SEQ.getAndIncrement() + "-s" + seed;
    }
}
