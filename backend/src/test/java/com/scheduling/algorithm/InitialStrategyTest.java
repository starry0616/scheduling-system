package com.scheduling.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段8.1 初始解策略测试:
 * <ol>
 *   <li>RANDOM 初始解合法性(Assignment 恒来自各自 candidates);</li>
 *   <li>RANDOM 初始解 seed 可重复;</li>
 *   <li>不同 seed 产生不同初始解;</li>
 *   <li>GREEDY 默认行为不变(3 参入口 == 显式 GREEDY);</li>
 *   <li>GREEDY / RANDOM 均能进入 SA 并正常结束;</li>
 *   <li>两种策略产出的 Assignment 均满足 candidate invariant;</li>
 *   <li>RANDOM 策略引擎级同 seed 完整复现(随机数与搜索阶段统一)。</li>
 * </ol>
 */
class InitialStrategyTest {

    /** 可行小实例: 4 个开课实例 x 每周 2 次课, 4 教师/4 班级/2 教室/5 天, 共 8 个 unit */
    private static SchedulingProblem buildProblem() {
        List<SchedulingUnit> units = new ArrayList<>();
        int idx = 0;
        for (int offering = 0; offering < 4; offering++) {
            for (int session = 0; session < 2; session++) {
                units.add(AlgorithmTestSupport.unit(idx++, 100L + offering, session,
                        1000L + offering * 100L + 1L, 100L + offering,
                        2, 1, AlgorithmTestSupport.normalRooms()));
            }
        }
        return AlgorithmTestSupport.problem(units);
    }

    private static SimulatedAnnealingParams modestParams() {
        return new SimulatedAnnealingParams(1000, 10, 0.1, 0.95, 300, 12, 3);
    }

    /** 断言 choices[i] 恒为 unit.candidates 内的合法下标(candidate invariant) */
    private static void assertLegalChoices(SchedulingProblem p, int[] choices) {
        assertEquals(p.getUnitCount(), choices.length, "choices 长度必须等于 unit 数");
        for (int i = 0; i < choices.length; i++) {
            SchedulingUnit unit = p.getUnit(i);
            assertTrue(choices[i] >= 0 && choices[i] < unit.candidateCount(),
                    "非法候选下标: unit=" + i + ", choice=" + choices[i]
                            + ", candidateCount=" + unit.candidateCount());
        }
    }

    // ---------- 1. RANDOM 初始解合法性 ----------

    @Test
    void random初始解_全部来自各自候选无非法下标() {
        SchedulingProblem p = buildProblem();
        int[] choices = InitialSolution.random(p, new Random(20260905L));
        assertLegalChoices(p, choices);
    }

    // ---------- 2. RANDOM 初始解 seed 可重复 ----------

    @Test
    void random初始解_同一seed两次生成完全一致() {
        SchedulingProblem p = buildProblem();
        assertArrayEquals(InitialSolution.random(p, new Random(7L)),
                InitialSolution.random(p, new Random(7L)),
                "同一 seed 的随机初始解必须逐位一致");
        assertArrayEquals(InitialSolution.random(p, new Random(20260905L)),
                InitialSolution.random(p, new Random(20260905L)),
                "同一 seed 的随机初始解必须逐位一致");
    }

    // ---------- 3. 不同 seed 产生不同初始解 ----------

    @Test
    void random初始解_不同seed产生不同初始解且仍合法() {
        SchedulingProblem p = buildProblem();
        int[] s1 = InitialSolution.random(p, new Random(1L));
        int[] s2 = InitialSolution.random(p, new Random(2L));
        assertFalse(Arrays.equals(s1, s2), "不同 seed 的随机初始解应不同(8 unit x 50 候选, 碰撞概率可忽略)");
        assertLegalChoices(p, s1);
        assertLegalChoices(p, s2);
    }

    // ---------- 4. GREEDY 默认行为不变 ----------

    @Test
    void greedy默认入口与显式GREEDY运行完全一致() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = modestParams();
        AnnealingResult def = SimulatedAnnealing.run(p, params, 20260905L);
        AnnealingResult greedy = SimulatedAnnealing.run(p, params, 20260905L, InitialStrategy.GREEDY);

        assertEquals(InitialStrategy.GREEDY, def.initialStrategy(), "默认入口必须记录为 GREEDY");
        assertEquals(InitialStrategy.GREEDY, greedy.initialStrategy());
        assertEquals(def.initialEnergy(), greedy.initialEnergy(), 0.0);
        assertEquals(def.initialHardViolation(), greedy.initialHardViolation());
        assertEquals(def.initialSoftPenalty(), greedy.initialSoftPenalty(), 0.0);
        assertEquals(def.bestEnergy(), greedy.bestEnergy(), 0.0);
        assertEquals(def.bestHardViolation(), greedy.bestHardViolation());
        assertEquals(def.bestSoftPenalty(), greedy.bestSoftPenalty(), 0.0);
        assertEquals(def.tempIterations(), greedy.tempIterations());
        assertEquals(def.totalNeighborEvaluations(), greedy.totalNeighborEvaluations());
        assertArrayEquals(def.best().toArray(), greedy.best().toArray(), "默认入口与显式 GREEDY 最优解必须一致");
        assertEquals(def.history(), greedy.history(), "默认入口与显式 GREEDY 的收敛历史序列必须一致");
    }

    // ---------- 5. 两种策略均能进入 SA 并正常结束 ----------

    @Test
    void greedy与random均能进入SA并正常结束() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = modestParams();
        for (InitialStrategy strategy : InitialStrategy.values()) {
            AnnealingResult result = SimulatedAnnealing.run(p, params, 20260905L, strategy);
            assertNotNull(result, strategy + " 应返回结果对象");
            assertEquals(strategy, result.initialStrategy(), "结果必须记录所用策略");
            assertEquals(p.getUnitCount(), result.best().size(), "最优解应覆盖全部 unit");
            assertTrue(result.tempIterations() >= 1, strategy + " 应完成至少 1 次温度迭代");
            assertTrue(result.totalNeighborEvaluations() > 0, strategy + " 应产生邻域评价");
            assertTrue(result.bestEnergy() <= result.initialEnergy() + 1e-9,
                    strategy + " 最优解能量不应高于初始解能量");
        }
    }

    // ---------- 6. 两种策略 Assignment 均满足 candidate invariant ----------

    @Test
    void 两种策略SA结果均满足candidateInvariant() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = modestParams();
        for (InitialStrategy strategy : InitialStrategy.values()) {
            for (long seed : new long[]{1L, 7L, 20260905L}) {
                AnnealingResult result = SimulatedAnnealing.run(p, params, seed, strategy);
                assertLegalChoices(p, result.best().toArray());
                assertTrue(result.bestHardViolation() >= 0);
                assertTrue(result.bestSoftPenalty() >= 0);
            }
        }
    }

    // ---------- 7. RANDOM 策略引擎级同 seed 完整复现(随机数统一) ----------

    @Test
    void random策略_引擎级同一seed两次运行完全一致() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = modestParams();
        AnnealingResult run1 = SimulatedAnnealing.run(p, params, 20260905L, InitialStrategy.RANDOM);
        AnnealingResult run2 = SimulatedAnnealing.run(p, params, 20260905L, InitialStrategy.RANDOM);

        assertEquals(InitialStrategy.RANDOM, run1.initialStrategy());
        assertEquals(run1.initialEnergy(), run2.initialEnergy(), 0.0, "随机初始解能量必须一致");
        assertEquals(run1.initialHardViolation(), run2.initialHardViolation());
        assertEquals(run1.initialSoftPenalty(), run2.initialSoftPenalty(), 0.0);
        assertEquals(run1.bestEnergy(), run2.bestEnergy(), 0.0, "同 seed 最优能量必须完全一致");
        assertEquals(run1.bestHardViolation(), run2.bestHardViolation());
        assertEquals(run1.bestSoftPenalty(), run2.bestSoftPenalty(), 0.0);
        assertEquals(run1.tempIterations(), run2.tempIterations());
        assertEquals(run1.totalNeighborEvaluations(), run2.totalNeighborEvaluations());
        assertArrayEquals(run1.best().toArray(), run2.best().toArray(), "同 seed 最优解必须逐位一致");
        assertEquals(run1.history(), run2.history(), "同 seed 历史采样序列必须完全一致");
    }
}
