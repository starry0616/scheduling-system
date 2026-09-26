package com.scheduling.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.scheduling.algorithm.AlgorithmTestSupport.candidateIndex;
import static com.scheduling.algorithm.AlgorithmTestSupport.normalRooms;
import static com.scheduling.algorithm.AlgorithmTestSupport.problem;
import static com.scheduling.algorithm.AlgorithmTestSupport.slotId;
import static com.scheduling.algorithm.AlgorithmTestSupport.unit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 硬/软约束惩罚公式验证(FitnessCalculator)。
 *
 * <p>每个场景刻意做成"最小干扰样本": 只构造触发目标约束的 unit 集合,
 * 并只断言被验证的约束分量, 其他分量的存在不影响断言。
 */
class FitnessConstraintsTest {

    private static final double DELTA = 1e-9;

    private FitnessBreakdown eval(List<SchedulingUnit> units, int[] choices) {
        SchedulingProblem p = problem(units);
        return FitnessCalculator.evaluate(p, choices);
    }

    @Test
    void h1_同一教师同一时间段冲突() {
        SchedulingUnit u0 = unit(0, 1, 0, 10, 1, 1, 1, normalRooms());
        SchedulingUnit u1 = unit(1, 2, 0, 10, 2, 1, 1, normalRooms()); // 教师同为 10, 班级不同
        int[] choices = new int[]{candidateIndex(u0, 101, slotId(1, 1)),
                candidateIndex(u1, 102, slotId(1, 1))};
        FitnessBreakdown b = eval(List.of(u0, u1), choices);
        assertEquals(1, b.getH1());
        assertEquals(0, b.getH2());
        assertEquals(0, b.getH3());
        assertEquals(0, b.getH7());
        assertEquals(1, b.getHardViolation());
    }

    @Test
    void h2_同一班级同一时间段冲突() {
        SchedulingUnit u0 = unit(0, 3, 0, 10, 5, 1, 1, normalRooms());
        SchedulingUnit u1 = unit(1, 4, 0, 11, 5, 1, 1, normalRooms()); // 班级同为 5, 教师不同
        int[] choices = new int[]{candidateIndex(u0, 101, slotId(1, 1)),
                candidateIndex(u1, 102, slotId(1, 1))};
        FitnessBreakdown b = eval(List.of(u0, u1), choices);
        assertEquals(0, b.getH1());
        assertEquals(1, b.getH2());
        assertEquals(0, b.getH3());
        assertEquals(1, b.getHardViolation());
    }

    @Test
    void h3_同一教室同一时间段冲突() {
        SchedulingUnit u0 = unit(0, 5, 0, 10, 6, 1, 1, normalRooms());
        SchedulingUnit u1 = unit(1, 6, 0, 11, 7, 1, 1, normalRooms()); // 教室同为 101
        int[] choices = new int[]{candidateIndex(u0, 101, slotId(1, 1)),
                candidateIndex(u1, 101, slotId(1, 1))};
        FitnessBreakdown b = eval(List.of(u0, u1), choices);
        assertEquals(0, b.getH1());
        assertEquals(0, b.getH2());
        assertEquals(1, b.getH3());
        assertEquals(1, b.getHardViolation());
    }

    @Test
    void h7_同一开课实例不同课次同一天() {
        // offering=9 每周 2 次课的两个 session, 均落在第 2 天(但时间段不同, 不触发 H1/H2)
        SchedulingUnit s0 = unit(0, 9, 0, 30, 9, 2, 1, normalRooms());
        SchedulingUnit s1 = unit(1, 9, 1, 30, 9, 2, 1, normalRooms());
        int[] choices = new int[]{candidateIndex(s0, 101, slotId(2, 1)),
                candidateIndex(s1, 102, slotId(2, 3))};
        FitnessBreakdown b = eval(List.of(s0, s1), choices);
        assertEquals(0, b.getH1());
        assertEquals(0, b.getH2());
        assertEquals(1, b.getH7(), "同 offering 两个课次不能同一天");
        assertEquals(1, b.getHardViolation());

        // 同 offering 两个课次分两天 => H7 = 0
        int[] spread = new int[]{candidateIndex(s0, 101, slotId(2, 1)),
                candidateIndex(s1, 102, slotId(4, 1))};
        FitnessBreakdown b2 = eval(List.of(s0, s1), spread);
        assertEquals(0, b2.getH7());
    }

    @Test
    void s1_教师不偏好时间段的课时数() {
        SchedulingUnit u = unit(0, 7, 0, 40, 11, 1, 1, normalRooms());
        // 教师 40 在 (周一, 第3大节) 标记为"不偏好(-1)"
        SchedulingProblem p = problem(List.of(u), Map.of(40L, Map.of(slotId(1, 3), -1)));
        int choice = candidateIndex(u, 101, slotId(1, 3));
        FitnessBreakdown b = FitnessCalculator.evaluate(p, new int[]{choice});
        assertEquals(1.0, b.getS1(), DELTA);
        assertEquals(0, b.getHardViolation());

        // 教师 40 在 (周三, 第1大节) 没有偏好配置 => 不罚
        int choiceOk = candidateIndex(u, 101, slotId(3, 1));
        FitnessBreakdown b2 = FitnessCalculator.evaluate(p, new int[]{choiceOk});
        assertEquals(0.0, b2.getS1(), DELTA);
    }

    @Test
    void s2_课次日期分布的间隔偏差() {
        // offering=8 每周 2 次: 落在周一、周三 => 实际间隔 2, 理想间隔 (5-1)/(2-1)=4, 罚 2
        SchedulingUnit s0 = unit(0, 8, 0, 50, 12, 2, 1, normalRooms());
        SchedulingUnit s1 = unit(1, 8, 1, 50, 12, 2, 1, normalRooms());
        int[] choices = new int[]{candidateIndex(s0, 101, slotId(1, 2)),
                candidateIndex(s1, 102, slotId(3, 2))};
        FitnessBreakdown b = eval(List.of(s0, s1), choices);
        assertEquals(2.0, b.getS2(), DELTA);
        assertEquals(0, b.getHardViolation());

        // 若落在周一、周五 => 间隔 4 = 理想间隔, S2=0
        int[] perfect = new int[]{candidateIndex(s0, 101, slotId(1, 2)),
                candidateIndex(s1, 102, slotId(5, 2))};
        FitnessBreakdown b2 = eval(List.of(s0, s1), perfect);
        assertEquals(0.0, b2.getS2(), DELTA);
    }

    @Test
    void s4_教师同一天连续课超过三节() {
        // 教师 80 在周二连续 p1..p4 => 连续段长 4 => S4 = 1
        SchedulingUnit a = unit(0, 81, 0, 80, 15, 1, 1, normalRooms());
        SchedulingUnit b = unit(1, 82, 0, 80, 16, 1, 1, normalRooms());
        SchedulingUnit c = unit(2, 83, 0, 80, 17, 1, 1, normalRooms());
        SchedulingUnit d = unit(3, 84, 0, 80, 18, 1, 1, normalRooms());
        int[] choices = new int[]{
                candidateIndex(a, 101, slotId(2, 1)),
                candidateIndex(b, 102, slotId(2, 2)),
                candidateIndex(c, 101, slotId(2, 3)),
                candidateIndex(d, 102, slotId(2, 4))};
        FitnessBreakdown bd = eval(List.of(a, b, c, d), choices);
        assertEquals(1.0, bd.getS4(), DELTA);
        assertEquals(0, bd.getHardViolation());
    }

    @Test
    void s5_学生一天内空课超过两个大节() {
        // 班级 14 一天只有第1大节与第5大节 => 中间空 3 个大节, 超容忍 2 => S5 = 1
        SchedulingUnit a = unit(0, 71, 0, 71, 14, 1, 1, normalRooms());
        SchedulingUnit b = unit(1, 72, 0, 72, 14, 1, 1, normalRooms());
        int[] choices = new int[]{candidateIndex(a, 101, slotId(1, 1)),
                candidateIndex(b, 102, slotId(1, 5))};
        FitnessBreakdown bd = eval(List.of(a, b), choices);
        assertEquals(1.0, bd.getS5(), DELTA);
        assertEquals(0, bd.getHardViolation());
    }

    @Test
    void s6_早晚节课时数() {
        SchedulingUnit u = unit(0, 9, 0, 60, 13, 1, 1, normalRooms());
        // (周一, 第1大节): S6 记 1; (周一, 第5大节): S6 记 1; (周三, 第3大节): 0
        FitnessBreakdown first = FitnessCalculator.evaluate(
                problem(List.of(u)), new int[]{candidateIndex(u, 101, slotId(1, 1))});
        FitnessBreakdown last = FitnessCalculator.evaluate(
                problem(List.of(u)), new int[]{candidateIndex(u, 101, slotId(1, 5))});
        FitnessBreakdown middle = FitnessCalculator.evaluate(
                problem(List.of(u)), new int[]{candidateIndex(u, 101, slotId(3, 3))});
        assertEquals(1.0, first.getS6(), DELTA);
        assertEquals(1.0, last.getS6(), DELTA);
        assertEquals(0.0, middle.getS6(), DELTA);
    }

    @Test
    void 硬约束权重大于软惩罚上界且可行解能量必低于任何含硬冲突的解() {
        // W_hard = floor(SoftMax) + 1 > SoftMax
        SchedulingUnit u0 = unit(0, 1, 0, 10, 1, 1, 1, normalRooms());
        SchedulingUnit u1 = unit(1, 2, 0, 11, 2, 1, 1, normalRooms());
        SchedulingProblem p = problem(List.of(u0, u1));
        assertTrue(p.getHardWeight() > p.getSoftMax(), "W_hard 必须大于 SoftMax");

        // 含 1 次硬冲突(H1)的解 vs 完全可行的解
        int[] conflicting = new int[]{candidateIndex(u0, 101, slotId(1, 1)),
                candidateIndex(u1, 101, slotId(1, 1))};
        int[] feasible = new int[]{candidateIndex(u0, 101, slotId(1, 1)),
                candidateIndex(u1, 102, slotId(2, 1))};
        FitnessBreakdown bad = FitnessCalculator.evaluate(p, conflicting);
        FitnessBreakdown good = FitnessCalculator.evaluate(p, feasible);
        assertEquals(1, bad.getHardViolation());
        assertEquals(0, good.getHardViolation());
        assertTrue(good.getEnergy() < bad.getEnergy(),
                "任何可行解的能量必须低于任何含硬冲突的解(W_hard > SoftMax 的推论)");
    }
}
