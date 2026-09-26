package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能量/适应度计算器(纯算法, V2.2 §7-§9)。
 *
 * <p>能量函数:
 * <pre>
 *   E(S) = W_hard * HardViolation(S) + Σ(wj * Sj(S))
 *   HardViolation(S) = H1 + H2 + H3 + H7(均为整数)
 *   软约束 S1..S6 原始量为 double(S2 因 g* = (daysPerWeek-1)/(k-1) 可为小数)。
 * </pre>
 *
 * <p>硬约束定义(全部基于 Assignment 的 occupiedSlotIds):
 * <ul>
 *   <li>H1: 同一教师在同一个 time_slot 上的 Assignment 数 > 1;</li>
 *   <li>H2: 同一班级在同一个 time_slot 上的 Assignment 数 > 1;</li>
 *   <li>H3: 同一教室在同一个 time_slot 上的 Assignment 数 > 1;</li>
 *   <li>H7: 同一 CourseOffering 的不同课次被安排在同一天。</li>
 * </ul>
 * 违反计数的口径为"超出第 1 个的占用数", 即 Σ max(0, count-1)。
 * 容量(H4)/类型(H5)/不可用时间已由候选生成阶段保证, 不进惩罚函数。
 *
 * <p>软约束定义:
 * <ul>
 *   <li>S1 教师时间偏好: 每占用 slot 落在该教师"不偏好(-1)"时间段则 +1;</li>
 *   <li>S2 日期分布均衡: 对每周 k 次课的 offering, 把 k 个课次按天升序排列,
 *       相邻间隔 gᵢ 与理想间隔 g* = (daysPerWeek-1)/(k-1) 的绝对偏差求和;
 *       k=1 记 0;</li>
 *   <li>S3 学生每天课量均衡: 每班级每天实际课次数与该班每周总课次数/每周天数
 *       的理想值的绝对偏差求和;</li>
 *   <li>S4 教师连续课节: 每教师每天连续占用 period 的段长超过 3 的部分求和;</li>
 *   <li>S5 学生空课时间: 每班级每天首尾课之间被空闲占掉的 period 数超过 2 的部分求和;</li>
 *   <li>S6 早晚节避免: 每占用 slot 处于当天第 1 大节或最后 1 大节则 +1。</li>
 * </ul>
 */
public final class FitnessCalculator {

    private FitnessCalculator() {
    }

    /**
     * 全量评估一个解, 返回能量分解。
     *
     * @param problem  排课问题
     * @param solution 解(choices)
     */
    public static FitnessBreakdown evaluate(SchedulingProblem problem, SchedulingSolution solution) {
        return evaluate(problem, solution.toArray());
    }

    /** 全量评估(内部 hot path 用 int[] 版本, 避免一次数组拷贝) */
    static FitnessBreakdown evaluate(SchedulingProblem problem, int[] choices) {
        StateCounters sc = StateCounters.build(problem, choices);

        double w1 = problem.getWTeacherPreference();
        double w2 = problem.getWCourseDistribution();
        double w3 = problem.getWStudentBalance();
        double w4 = problem.getWTeacherContinuous();
        double w5 = problem.getWStudentIdle();
        double w6 = problem.getWMorningEvening();

        // ---- S1 教师时间偏好 ----
        double s1 = 0;
        for (int i = 0; i < problem.getUnitCount(); i++) {
            SchedulingUnit unit = problem.getUnit(i);
            CandidateAssignment cand = unit.getCandidates().get(choices[i]);
            for (long slotId : cand.occupiedSlotIdsUnsafe()) {
                if (problem.teacherPreferenceLevel(unit.getTeacherId(), slotId) == -1) {
                    s1 += 1;
                }
            }
        }

        // ---- S2 日期分布均衡(每 offering 的课次按天间隔与理想间隔的偏差) ----
        double s2 = 0;
        List<Integer> dayValues = problem.getDayValues();
        double daySpan = dayValues.get(dayValues.size() - 1) - dayValues.get(0);   // 如 1..5 → 4
        for (List<Integer> daysOfOffer : sc.offeringAssignmentDays.values()) {
            int k = daysOfOffer.size();
            if (k < 2) {
                continue;
            }
            List<Integer> sorted = new ArrayList<>(daysOfOffer);
            sorted.sort(Integer::compareTo);
            double idealGap = daySpan / (k - 1.0);
            for (int j = 0; j < k - 1; j++) {
                int gap = sorted.get(j + 1) - sorted.get(j);
                s2 += Math.abs(gap - idealGap);
            }
        }

        // ---- S3 学生每天课量均衡 ----
        double s3 = 0;
        for (long classId : problem.getDistinctClassIds()) {
            int total = sc.classTotalSessions.getOrDefault(classId, 0);
            double ideal = total / (double) dayValues.size();
            Map<Integer, Integer> dayCounts = sc.classDaySessionCount.get(classId);
            for (int d : dayValues) {
                int actual = dayCounts == null ? 0 : dayCounts.getOrDefault(d, 0);
                s3 += Math.abs(actual - ideal);
            }
        }

        // ---- S4 教师连续课节(同一天每个连续段段长 > 3 的部分分别求和) ----
        double s4 = 0;
        for (Map<Integer, Set<Integer>> dayPeriods : sc.teacherDayPeriods.values()) {
            for (Set<Integer> periods : dayPeriods.values()) {
                s4 += consecutiveExcess(periods);
            }
        }

        // ---- S5 学生空课时间(每班级每天首尾课之间空闲 period 数超过 2 的部分) ----
        double s5 = 0;
        for (Map<Integer, Set<Integer>> dayPeriods : sc.classDayPeriods.values()) {
            for (Set<Integer> periods : dayPeriods.values()) {
                int interiorIdle = interiorIdleSlots(periods);
                if (interiorIdle > 2) {
                    s5 += interiorIdle - 2;
                }
            }
        }

        // ---- S6 早晚节避免 ----
        double s6 = 0;
        int lastPeriod = problem.getPeriodsPerDay();
        for (int i = 0; i < problem.getUnitCount(); i++) {
            CandidateAssignment cand = problem.getUnit(i).getCandidates().get(choices[i]);
            for (int period : cand.occupiedPeriodsUnsafe()) {
                if (period == 1 || period == lastPeriod) {
                    s6 += 1;
                }
            }
        }

        double hardTotal = sc.hardViolationTotal();
        double energy = problem.getHardWeight() * hardTotal
                + w1 * s1 + w2 * s2 + w3 * s3 + w4 * s4 + w5 * s5 + w6 * s6;

        return new FitnessBreakdown(energy,
                sc.getH1(), sc.getH2(), sc.getH3(), sc.getH7(),
                s1, s2, s3, s4, s5, s6);
    }

    /**
     * 同一天连续 period 段的惩罚: 对每个"相邻 period 差 1"构成的连续段,
     * 若段长 > 3, 累加 (段长 - 3); 多段各自计算并求和。
     * 例如 periods={1,2,3,4} 与 {1,2,3,4,5} 分别记 +1、+2。
     */
    private static int consecutiveExcess(Set<Integer> periods) {
        Integer[] arr = periods.toArray(new Integer[0]);
        Arrays.sort(arr);
        int penalty = 0;
        int run = 0;
        int prev = Integer.MIN_VALUE;
        for (int period : arr) {
            if (run == 0) {
                run = 1;
            } else if (period == prev + 1) {
                run++;
            } else {
                if (run > 3) {
                    penalty += run - 3;
                }
                run = 1;
            }
            prev = period;
        }
        if (run > 3) {
            penalty += run - 3;
        }
        return penalty;
    }

    /** 当天首尾课之间被空闲占掉的 period 数(连续段之间 gap-1 之和) */
    private static int interiorIdleSlots(Set<Integer> periods) {
        Integer[] arr = periods.toArray(new Integer[0]);
        Arrays.sort(arr);
        int idle = 0;
        for (int i = 1; i < arr.length; i++) {
            idle += (arr[i] - arr[i - 1] - 1);
        }
        return idle;
    }
}
