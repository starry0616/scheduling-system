package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解的统计状态(包内私有, 供 FitnessCalculator / ConflictRepair 一次性构建后复用)。
 *
 * <p>给定一份 choices(每个 unit 选中的候选下标), 汇总出约束计算所需的全部中间结构,
 * 一次遍历完成:
 * <pre>
 *   硬约束:  teacher/class/room 在 (资源, time_slot) 维度上的占用计数(H1/H2/H3),
 *            开课实例的 天->次数(H7);
 *   软约束:  S1 直接基于教师偏好查询; S2 基于开课实例的课次日期分布;
 *            S3/S5 基于班级每天占用; S4 基于教师每天连续占用。
 * </pre>
 *
 * <p>所有约束一律基于候选的 occupiedSlotIds 计算(实验课连续 2 个大节按同一课次处理)。
 */
final class StateCounters {

    /** 当前统计对应的候选选择(快照) */
    final int[] choices;

    // ---- H1/H2/H3 占用计数: 资源id -> timeSlotId -> 占用次数 ----
    final Map<Long, Map<Long, Integer>> teacherSlot = new HashMap<>();
    final Map<Long, Map<Long, Integer>> classSlot = new HashMap<>();
    final Map<Long, Map<Long, Integer>> roomSlot = new HashMap<>();

    // ---- H7: 开课实例 -> 天 -> 课次数(每 Assignment 记 1 次) ----
    final Map<Long, Map<Integer, Integer>> offeringDayCount = new HashMap<>();
    // 同一 offering 各 Assignment 的天序列(按 unit 顺序, 供 S2 计算)
    final Map<Long, List<Integer>> offeringAssignmentDays = new HashMap<>();

    // ---- 软约束中间量 ----
    /** 教师 -> 天 -> period 集合(S4) */
    final Map<Long, Map<Integer, Set<Integer>>> teacherDayPeriods = new HashMap<>();
    /** 班级 -> 天 -> period 集合(S5) */
    final Map<Long, Map<Integer, Set<Integer>>> classDayPeriods = new HashMap<>();
    /** 班级 -> 天 -> 当天课次数(S3, 每个 Assignment 记 1 次) */
    final Map<Long, Map<Integer, Integer>> classDaySessionCount = new HashMap<>();
    /** 班级 -> 每周总课次数(S3 理想分布的分母) */
    final Map<Long, Integer> classTotalSessions = new HashMap<>();

    // ---- 汇总值 ----
    private int h1;
    private int h2;
    private int h3;
    private int h7;

    private StateCounters(int[] choices) {
        this.choices = choices.clone();
    }

    /** 单次遍历构建统计(choices[i] 必须是 unit i 候选列表内的合法下标) */
    static StateCounters build(SchedulingProblem p, int[] choices) {
        StateCounters sc = new StateCounters(choices);
        SchedulingUnit[] units = p.getUnits().toArray(new SchedulingUnit[0]);

        for (int i = 0; i < units.length; i++) {
            SchedulingUnit unit = units[i];
            int choice = choices[i];
            if (choice < 0 || choice >= unit.candidateCount()) {
                throw new IllegalArgumentException("非法候选下标: unit=" + i + ", choice=" + choice);
            }
            CandidateAssignment cand = unit.getCandidates().get(choice);
            long[] occSlots = cand.occupiedSlotIdsUnsafe();
            int[] occPeriods = cand.occupiedPeriodsUnsafe();
            long teacherId = unit.getTeacherId();
            long classroomId = cand.getClassroomId();
            int day = cand.getDayOfWeek();

            // H1 教师占用
            incrementEach(sc.teacherSlot, teacherId, occSlots);
            // H3 教室占用
            incrementEach(sc.roomSlot, classroomId, occSlots);
            // H2 班级占用 + S3/S5 班级维度
            for (long classId : unit.classIdsUnsafe()) {
                incrementEach(sc.classSlot, classId, occSlots);
                addPeriods(sc.classDayPeriods, classId, day, occPeriods);
                sc.classDaySessionCount.computeIfAbsent(classId, k -> new HashMap<>())
                        .merge(day, 1, Integer::sum);
                sc.classTotalSessions.merge(classId, 1, Integer::sum);
            }
            // H7 同 offering 同天
            sc.offeringDayCount.computeIfAbsent(unit.getCourseOfferingId(), k -> new HashMap<>())
                    .merge(day, 1, Integer::sum);
            sc.offeringAssignmentDays.computeIfAbsent(unit.getCourseOfferingId(), k -> new ArrayList<>())
                    .add(day);
            // S4 教师每天 period 集合
            addPeriods(sc.teacherDayPeriods, teacherId, day, occPeriods);
        }
        sc.computeHardTotals();
        return sc;
    }

    // ---------- 硬约束汇总 ----------

    private void computeHardTotals() {
        this.h1 = sumExcess(teacherSlot);
        this.h2 = sumExcess(classSlot);
        this.h3 = sumExcess(roomSlot);
        this.h7 = 0;
        for (Map<Integer, Integer> dayCounts : offeringDayCount.values()) {
            for (int count : dayCounts.values()) {
                if (count > 1) {
                    h7 += count - 1;
                }
            }
        }
    }

    /** Σ max(0, count - 1): 同一 (资源, timeSlot) 上超出第 1 个的占用数 */
    private static int sumExcess(Map<Long, Map<Long, Integer>> resourceSlot) {
        int total = 0;
        for (Map<Long, Integer> slotCounts : resourceSlot.values()) {
            for (int count : slotCounts.values()) {
                if (count > 1) {
                    total += count - 1;
                }
            }
        }
        return total;
    }

    int getH1() {
        return h1;
    }

    int getH2() {
        return h2;
    }

    int getH3() {
        return h3;
    }

    int getH7() {
        return h7;
    }

    int hardViolationTotal() {
        return h1 + h2 + h3 + h7;
    }

    /**
     * 判定某 unit 是否参与了硬违反(H1/H2/H3 中该资源组合计数>1, 或 H7 该 offering 当天重复)。
     * ConflictRepair 据此只对真正冲突的 unit 尝试重排。
     */
    boolean unitHasHardConflict(SchedulingProblem p, int unitIndex) {
        SchedulingUnit unit = p.getUnit(unitIndex);
        CandidateAssignment cand = unit.getCandidates().get(choices[unitIndex]);
        long[] occSlots = cand.occupiedSlotIdsUnsafe();

        if (hasExcess(teacherSlot, unit.getTeacherId(), occSlots)) {
            return true;
        }
        if (hasExcess(roomSlot, cand.getClassroomId(), occSlots)) {
            return true;
        }
        for (long classId : unit.classIdsUnsafe()) {
            if (hasExcess(classSlot, classId, occSlots)) {
                return true;
            }
        }
        Map<Integer, Integer> dayCounts = offeringDayCount.get(unit.getCourseOfferingId());
        return dayCounts != null && dayCounts.getOrDefault(cand.getDayOfWeek(), 0) > 1;
    }

    /** 找出全部参与硬违反的 unit 下标(按 unit 顺序稳定) */
    int[] hardConflictingUnitIndices(SchedulingProblem p) {
        List<Integer> conflicts = new ArrayList<>();
        for (int i = 0; i < p.getUnitCount(); i++) {
            if (unitHasHardConflict(p, i)) {
                conflicts.add(i);
            }
        }
        return conflicts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean hasExcess(Map<Long, Map<Long, Integer>> resourceSlot, long resourceId, long[] slots) {
        Map<Long, Integer> slotCounts = resourceSlot.get(resourceId);
        if (slotCounts == null) {
            return false;
        }
        for (long slot : slots) {
            if (slotCounts.getOrDefault(slot, 0) > 1) {
                return true;
            }
        }
        return false;
    }

    // ---------- 工具 ----------

    private static void incrementEach(Map<Long, Map<Long, Integer>> resourceSlot,
                                      long resourceId, long[] slots) {
        Map<Long, Integer> counts = resourceSlot.computeIfAbsent(resourceId, k -> new HashMap<>());
        for (long slot : slots) {
            counts.merge(slot, 1, Integer::sum);
        }
    }

    private static void addPeriods(Map<Long, Map<Integer, Set<Integer>>> resourceDayPeriods,
                                   long resourceId, int day, int[] periods) {
        Set<Integer> periodSet = resourceDayPeriods
                .computeIfAbsent(resourceId, k -> new HashMap<>())
                .computeIfAbsent(day, k -> new LinkedHashSet<>());
        for (int period : periods) {
            periodSet.add(period);
        }
    }
}
