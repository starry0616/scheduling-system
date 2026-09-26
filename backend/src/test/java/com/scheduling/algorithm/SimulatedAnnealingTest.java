package com.scheduling.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.scheduling.algorithm.AlgorithmTestSupport.normalRooms;
import static com.scheduling.algorithm.AlgorithmTestSupport.problem;
import static com.scheduling.algorithm.AlgorithmTestSupport.unit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模拟退火引擎测试: 收敛性 / 同种子可复现 / 输出合法性 / 数据不可行报错。
 */
class SimulatedAnnealingTest {

    /** 适度的可行小实例: 4 个开课实例 x 每周 2 次课, 4 教师/4 班级/2 教室/5 天 */
    private static SchedulingProblem buildFeasibleProblem() {
        List<SchedulingUnit> units = new ArrayList<>();
        int idx = 0;
        for (int offering = 0; offering < 4; offering++) {
            for (int session = 0; session < 2; session++) {
                long teacherId = 1000L + offering * 100L + 1L;
                long classId = 100L + offering;
                long offeringId = 100L + offering;
                units.add(unit(idx++, offeringId, session, teacherId, classId, 2, 1, normalRooms()));
            }
        }
        return problem(units);
    }

    private static SimulatedAnnealingParams modestParams() {
        return new SimulatedAnnealingParams(1000, 10, 0.1, 0.95, 300, 12, 3);
    }

    @Test
    void 可行小实例_收敛到硬冲突为0() {
        SchedulingProblem p = buildFeasibleProblem();
        SimulatedAnnealingParams params = modestParams();

        AnnealingResult result = SimulatedAnnealing.run(p, params, 20260905L);

        assertTrue(result.isFeasible(), "可行小实例应收敛到硬冲突 0, bestHardViolation="
                + result.bestHardViolation());
        assertTrue(result.bestEnergy() <= result.initialEnergy() + 1e-9,
                "最优解能量不应高于初始解能量");
        // 最优解各候选下标合法
        SchedulingSolution best = result.best();
        for (int i = 0; i < best.size(); i++) {
            int choice = best.getChoice(i);
            SchedulingUnit unit = p.getUnit(i);
            assertTrue(choice >= 0 && choice < unit.candidateCount(),
                    "候选下标越界: unit=" + i + ", choice=" + choice);
        }
        // 结果能量与全量重算一致
        FitnessBreakdown recheck = FitnessCalculator.evaluate(p, best);
        assertEquals(result.bestEnergy(), recheck.getEnergy(), 1e-6);
        assertEquals(result.bestHardViolation(), recheck.getHardViolation());
        assertEquals(result.bestSoftPenalty(), recheck.getSoftPenalty(), 1e-6);
    }

    @Test
    void 同一随机种子_两次运行完全一致() {
        SchedulingProblem p = buildFeasibleProblem();
        SimulatedAnnealingParams params = modestParams();

        AnnealingResult run1 = SimulatedAnnealing.run(p, params, 20260905L);
        AnnealingResult run2 = SimulatedAnnealing.run(p, params, 20260905L);

        assertEquals(run1.bestEnergy(), run2.bestEnergy(), 0.0, "同种子能量必须完全一致");
        assertEquals(run1.bestHardViolation(), run2.bestHardViolation());
        assertEquals(run1.bestSoftPenalty(), run2.bestSoftPenalty(), 0.0);
        assertEquals(run1.tempIterations(), run2.tempIterations());
        assertEquals(run1.totalNeighborEvaluations(), run2.totalNeighborEvaluations());
        assertEquals(run1.history(), run2.history(), "同种子历史采样序列必须完全一致");
        SchedulingSolution s1 = run1.best();
        SchedulingSolution s2 = run2.best();
        for (int i = 0; i < s1.size(); i++) {
            assertEquals(s1.getChoice(i), s2.getChoice(i), "同种子最优解各 unit 选择应一致: unit=" + i);
        }
    }

    @Test
    void 不同种子_结果允许不同但均合法() {
        SchedulingProblem p = buildFeasibleProblem();
        SimulatedAnnealingParams params = modestParams();
        AnnealingResult a = SimulatedAnnealing.run(p, params, 1L);
        AnnealingResult b = SimulatedAnnealing.run(p, params, 2L);
        assertTrue(a.isFeasible());
        assertTrue(b.isFeasible());
        // 不为空且无越界
        assertTrue(a.best().size() == p.getUnitCount() && b.best().size() == p.getUnitCount());
    }

    @Test
    void 数据不可行_无可选候选时抛出清晰异常() {
        SchedulingUnit infeasible = unit(0, 1, 0, 10, 1, 1, 1,
                java.util.List.of(), 40, "NORMAL", java.util.Set.of(),
                java.util.Set.of(), java.util.Map.of());
        // 候选列表为空(无任何教室), 构造问题必须直接报错
        assertThrows(IllegalArgumentException.class,
                () -> problem(java.util.List.of(infeasible)),
                "无候选的排课单元应在进入算法前报出数据不可行");
    }
}
