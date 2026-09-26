package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * 模拟退火主引擎(纯算法, V2.2 §16)。
 *
 * <p>完整流程:
 * <pre>
 *   1. S = 初始解(默认贪心 GREEDY, 阶段8.1起可指定 RANDOM 随机初始, 见 InitialStrategy);
 *   2. 初始温度 T0 = clamp(E(S) * 1.5, minInitialTemp, maxInitialTemp)(自适应);
 *   3. 外循环: while T > minTemp 且 外循环次数未达上限:
 *        a. 内循环 neighborsPerTemp 次: 生成邻域 S'(MOVE/SWAP/CHANGE_ROOM),
 *           计算能量差 dE = E(S') - E(S);
 *           Metropolis 准则: dE < 0 或 random() &lt; exp(-dE/T) 则接受;
 *           若 E(S') &lt; E_best 则更新最优解;
 *        b. 降温: T *= coolingRate;
 *        c. 冲突修复(全局评价)并刷新能量;
 *        d. 记录采样点(温度/当前能量/最优能量/硬/软);
 *   4. 主循环结束后再做一次最终冲突修复;
 *   5. 返回最优解与运行统计。
 * </pre>
 *
 * <p>随机种子: seed 为空时用系统时间自动播种(不可复现); 传入 seed 时严格可复现
 * (引擎全部随机源由同一 Random 派生, 且统计结构不依赖无序集合的迭代次序)。
 *
 * <p>初始解策略: run(problem, params, seed) 恒等于 run(problem, params, seed, GREEDY),
 * 保证原业务行为不变; 传 RANDOM 时初始解与搜索阶段共享同一 Random 实例(同 seed 完整复现)。
 *
 * <p>说明: 修复阶段以"硬约束优先"为原则——当修复后能量未升但硬违反下降时也会采纳,
 * 并始终记录能量最低的最优解(含初始解在内)。
 */
public final class SimulatedAnnealing {

    /** 单次邻域生成的尝试上限(若某次算子不适用则换一种重试) */
    private static final int MAX_NEIGHBOR_ATTEMPTS = 24;

    private SimulatedAnnealing() {
    }

    /** 以 GREEDY 为默认初始解策略的运行入口(与原行为完全一致) */
    public static AnnealingResult run(SchedulingProblem problem,
                                      SimulatedAnnealingParams params,
                                      Long seed) {
        return run(problem, params, seed, InitialStrategy.GREEDY);
    }

    /**
     * 指定初始解策略的运行入口(阶段8.1)。
     *
     * <p>GREEDY = 贪心初始解(默认); RANDOM = 每个 unit 在自身候选中随机取一个。
     * 初始解选择发生在 rng 建立之后, RANDOM 会先消耗一段随机序列, 使后续搜索路径随之改变。
     */
    public static AnnealingResult run(SchedulingProblem problem,
                                      SimulatedAnnealingParams params,
                                      Long seed,
                                      InitialStrategy initialStrategy) {
        InitialStrategy effective = Objects.requireNonNull(initialStrategy, "initialStrategy");
        Random rng = (seed == null) ? new Random() : new Random(seed);
        NeighborhoodOperator operator = new NeighborhoodOperator(rng);

        // ---- 1. 初始解(默认贪心; 可选随机) ----
        int[] cur = (effective == InitialStrategy.RANDOM)
                ? InitialSolution.random(problem, rng)
                : InitialSolution.greedy(problem);
        FitnessBreakdown curBd = FitnessCalculator.evaluate(problem, cur);
        double curEnergy = curBd.getEnergy();
        FitnessBreakdown initialBd = curBd;   // 初始解指标(结果落库 initial_fitness 口径)

        int[] best = cur.clone();
        double bestEnergy = curEnergy;

        // ---- 2. 自适应初始温度 ----
        double T = Math.min(params.maxInitialTemp(),
                Math.max(params.minInitialTemp(), curEnergy * 1.5));

        int tempIterations = 0;
        long neighborEvals = 0;
        boolean stop = false;
        List<IterationPoint> history = new ArrayList<>();

        // ---- 3. 主循环 ----
        while (!stop && T > params.minTemp() && tempIterations < params.maxTempIterations()) {

            for (int inner = 0; inner < params.neighborsPerTemp(); inner++) {
                int[] cand = generateNeighbor(problem, cur, operator);
                if (cand == null) {
                    // 退化: 无任何合法邻域可生成(所有 unit 均单候选等), 提前结束
                    stop = true;
                    break;
                }
                neighborEvals++;
                FitnessBreakdown candBd = FitnessCalculator.evaluate(problem, cand);
                double candEnergy = candBd.getEnergy();
                if (candEnergy < curEnergy
                        || rng.nextDouble() < Math.exp((curEnergy - candEnergy) / T)) {
                    cur = cand;
                    curEnergy = candEnergy;
                    curBd = candBd;
                    if (curEnergy < bestEnergy) {
                        best = cur.clone();
                        bestEnergy = curEnergy;
                        if (bestEnergy == 0.0) {
                            stop = true;
                        }
                    }
                }
            }

            // b. 降温
            T *= params.coolingRate();
            tempIterations++;

            // c. 冲突修复(每温度迭代一次)
            if (params.maxRepairAttempts() > 0) {
                cur = applyRepairIfBeneficial(problem, cur, curBd, params.maxRepairAttempts(), rng);
                curBd = FitnessCalculator.evaluate(problem, cur);
                curEnergy = curBd.getEnergy();
                if (curEnergy < bestEnergy) {
                    best = cur.clone();
                    bestEnergy = curEnergy;
                    if (bestEnergy == 0.0) {
                        stop = true;
                    }
                }
            }

            // d. 采样
            history.add(new IterationPoint(tempIterations, T, curEnergy, bestEnergy,
                    curBd.getHardViolation(), curBd.getSoftPenalty()));
        }

        // ---- 4. 最终冲突修复 ----
        if (!stop && params.maxRepairAttempts() > 0) {
            cur = applyRepairIfBeneficial(problem, cur, curBd, params.maxRepairAttempts(), rng);
            curBd = FitnessCalculator.evaluate(problem, cur);
            curEnergy = curBd.getEnergy();
            if (curEnergy < bestEnergy) {
                best = cur.clone();
                bestEnergy = curEnergy;
            }
        }

        // ---- 5. 汇总 ----
        FitnessBreakdown bestBd = FitnessCalculator.evaluate(problem, best);
        SchedulingSolution bestSolution = new SchedulingSolution(best);
        return new AnnealingResult(bestSolution, bestEnergy,
                bestBd.getHardViolation(), bestBd.getSoftPenalty(),
                initialBd.getEnergy(), initialBd.getHardViolation(), initialBd.getSoftPenalty(),
                tempIterations, neighborEvals, List.copyOf(history), effective);
    }

    /**
     * 冲突修复采纳规则(硬约束优先):
     * <ul>
     *   <li>能量下降 ⟹ 采纳;</li>
     *   <li>能量未降但硬违反下降 ⟹ 采纳(宁可增加软惩罚也要消除硬冲突, 可行性优先);</li>
     *   <li>其余情况不采纳, 保留原解。</li>
     * </ul>
     */
    private static int[] applyRepairIfBeneficial(SchedulingProblem problem, int[] cur,
                                                 FitnessBreakdown curBd,
                                                 int maxRounds, Random rng) {
        int[] repaired = ConflictRepair.repair(problem, cur, maxRounds, rng);
        FitnessBreakdown repairedBd = FitnessCalculator.evaluate(problem, repaired);
        boolean hardBetter = repairedBd.getHardViolation() < curBd.getHardViolation();
        boolean energyBetter = repairedBd.getEnergy() < curBd.getEnergy();
        return (energyBetter || hardBetter) ? repaired : cur;
    }

    /** 尝试生成一个合法邻域; 连续 MAX_NEIGHBOR_ATTEMPTS 次失败返回 null(退化) */
    private static int[] generateNeighbor(SchedulingProblem problem, int[] cur,
                                          NeighborhoodOperator operator) {
        for (int attempt = 0; attempt < MAX_NEIGHBOR_ATTEMPTS; attempt++) {
            int[] cand = cur.clone();
            if (operator.apply(problem, cand)) {
                return cand;
            }
        }
        return null;
    }
}
