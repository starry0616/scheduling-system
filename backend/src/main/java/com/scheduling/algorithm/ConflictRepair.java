package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 冲突修复器(纯算法, V2.2 §13)。
 *
 * <p>对存在硬冲突的解做"全局评价的贪心修复":
 * <pre>
 *   每轮:
 *     1. 找出所有参与硬冲突的 unit(H1/H2/H3 所在资源组合计数>1, 或 H7 同 offering 同天重复);
 *     2. 对每个冲突 unit, 依次试其各候选(候选过多时抽样, 见 REPAIR_CANDIDATE_CAP):
 *        把该 unit 临时换成候选后, 用全局硬违反 HardViolation(S) 评价;
 *     3. 只有候选使全局硬违反严格下降才执行替换;
 *     4. 若某轮没有任何替换能减少全局硬违反, 提前结束。
 * </pre>
 *
 * <p>关键: 评价的是全局 HardViolation, 而非 unit 自身冲突数——避免"A 冲突少了、
 * 但与别的课新撞上"的局部假象。修复不保证 100% 消除冲突(数据本身不可行时任何
 * 算法都无法得到可行解), 论文如实报告成功率。
 */
public final class ConflictRepair {

    /** 候选数超过该值时, 每轮只抽样评估这么多候选(防不可行数据下失控) */
    private static final int REPAIR_CANDIDATE_CAP = 500;

    private ConflictRepair() {
    }

    /**
     * 就地返回修复后的解(新数组)。
     *
     * @param p          排课问题
     * @param current    当前解(choices)
     * @param maxRounds  最大修复轮数
     * @param rng        随机源(抽样用, 保证种子可复现)
     */
    public static int[] repair(SchedulingProblem p, int[] current, int maxRounds, Random rng) {
        int[] sol = current.clone();
        if (maxRounds <= 0) {
            return sol;
        }
        for (int round = 0; round < maxRounds; round++) {
            StateCounters sc = StateCounters.build(p, sol);
            int curHard = sc.hardViolationTotal();
            if (curHard == 0) {
                break;
            }
            int[] conflicting = sc.hardConflictingUnitIndices(p);
            boolean improved = false;

            for (int unitIdx : conflicting) {
                SchedulingUnit unit = p.getUnit(unitIdx);
                int curChoice = sol[unitIdx];
                List<Integer> evalOrder = candidateEvaluationOrder(unit, rng);

                int bestChoice = -1;
                int bestHard = curHard;
                for (int ci : evalOrder) {
                    if (ci == curChoice) {
                        continue;
                    }
                    sol[unitIdx] = ci;
                    int newHard = StateCounters.build(p, sol).hardViolationTotal();
                    sol[unitIdx] = curChoice;
                    if (newHard < bestHard) {
                        bestHard = newHard;
                        bestChoice = ci;
                    }
                }
                if (bestChoice >= 0) {
                    sol[unitIdx] = bestChoice;
                    curHard = bestHard;
                    improved = true;
                }
            }
            if (!improved) {
                break;
            }
        }
        return sol;
    }

    /** 候选评估顺序: 少时全量(稳定顺序), 多时随机抽样 REPAIR_CANDIDATE_CAP 个(防失控) */
    private static List<Integer> candidateEvaluationOrder(SchedulingUnit unit, Random rng) {
        int size = unit.candidateCount();
        if (size <= REPAIR_CANDIDATE_CAP) {
            List<Integer> all = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                all.add(i);
            }
            return all;
        }
        // 抽样: 洗牌后取前 REPAIR_CANDIDATE_CAP 个
        List<Integer> all = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            all.add(i);
        }
        for (int i = size - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = all.get(i);
            all.set(i, all.get(j));
            all.set(j, tmp);
        }
        return new ArrayList<>(all.subList(0, REPAIR_CANDIDATE_CAP));
    }
}
