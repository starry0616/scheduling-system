package com.scheduling.algorithm;

/**
 * 模拟退火初始解策略(阶段8.1)。
 *
 * <p>GREEDY = 贪心启发式初始解(系统默认, 与原业务流程行为一致);
 * RANDOM  = 每个 SchedulingUnit 在其候选列表中等概率随机选取(实验对照用)。
 *
 * <p>选择发生在 SimulatedAnnealing.run 入口; RANDOM 初始解复用引擎同一
 * Random/seed 体系, 相同 problem + 参数 + seed + 策略 严格可复现。
 */
public enum InitialStrategy {
    GREEDY,
    RANDOM
}
