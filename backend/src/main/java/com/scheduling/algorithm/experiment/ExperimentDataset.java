package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.SchedulingProblem;

/**
 * 一个标准实验数据集 = SchedulingProblem + 摘要(阶段8.2-B)。
 *
 * <p>problem 可直接交给 SimulatedAnnealing.run(...) 执行实验;
 * summary 用于论文数据描述与规模校验。
 */
public record ExperimentDataset(
        SchedulingProblem problem,
        DatasetSummary summary) {
}
