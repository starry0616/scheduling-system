package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 贪心初始解构造器(V2.2 §12)。
 *
 * <p>策略:
 * <pre>
 *   1. 按 每周课次数(weeklySessions) 降序 排序所有 unit(先排每周课次多的 offering, 难度大);
 *      次级排序 候选数升序(越难安排的越先), 再按 unitIndex 保证稳定;
 *   2. 依次为每个 unit 选候选: 遍历其 candidates, 选取"与已排 assignment 新增硬冲突数最小"
 *      的候选, 一旦出现新增 0 冲突立即选用(取首个即可);
 *   3. 冲突按 H1/H2/H3/H7 的统一口径计算"放入该候选后全局硬违反的增量":
 *      对每个占用 time_slot, 检查 教师/各班级/教室 是否已在该 slot 被占用
 *      (每命中一个 (资源, slot) 组合计 +1), 并计入同 offering 已有同天课次(+1)。
 * </pre>
 *
 * <p>该增量口径与全局硬违反口径一致: 贪心过程保证全局硬违反单调非减地构建,
 * 每次选择使累计硬违反尽量小。返回的解作为 SA 的初始解。
 */
public final class InitialSolution {

    private InitialSolution() {
    }

    /** 生成贪心初始解(choices, 长度为 unitCount) */
    public static int[] greedy(SchedulingProblem p) {
        int n = p.getUnitCount();

        // 1. 排序: weeklySessions 降序 -> 候选数升序 -> unitIndex 升序
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            SchedulingUnit ua = p.getUnit(a);
            SchedulingUnit ub = p.getUnit(b);
            int cmp = Integer.compare(ub.getWeeklySessions(), ua.getWeeklySessions());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(ua.candidateCount(), ub.candidateCount());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(a, b);
        });

        Map<Long, Map<Long, Integer>> teacherSlot = new HashMap<>();
        Map<Long, Map<Long, Integer>> classSlot = new HashMap<>();
        Map<Long, Map<Long, Integer>> roomSlot = new HashMap<>();
        Map<Long, Map<Integer, Integer>> offeringDayCount = new HashMap<>();

        int[] choices = new int[n];
        Arrays.fill(choices, -1);

        // 2. 依序为每个 unit 选"新增冲突最小"的候选
        for (int idx : order) {
            SchedulingUnit unit = p.getUnit(idx);
            List<CandidateAssignment> candidates = unit.getCandidates();
            int bestChoice = -1;
            int bestAdded = Integer.MAX_VALUE;
            for (int ci = 0; ci < candidates.size(); ci++) {
                CandidateAssignment cand = candidates.get(ci);
                int added = addedHardConflicts(unit, cand, teacherSlot, classSlot, roomSlot, offeringDayCount);
                if (added < bestAdded) {
                    bestAdded = added;
                    bestChoice = ci;
                    if (added == 0) {
                        break;
                    }
                }
            }
            choices[idx] = bestChoice;
            applyAdd(unit, candidates.get(bestChoice), teacherSlot, classSlot, roomSlot, offeringDayCount);
        }
        return choices;
    }

    /**
     * 生成随机初始解(阶段8.1): 对每个 SchedulingUnit 在其自身 candidates 中等概率随机选一个。
     *
     * <p>约定与保证:
     * <ul>
     *   <li>choices[i] 严格取自 unit.candidates 的合法下标, 不会产生非法 room/time
     *       (候选在组装阶段已过滤容量/类型/不可用时间);</li>
     *   <li>随机源由调用方传入(SA 引擎传入与搜索阶段同一 Random), 相同
     *       problem + Random(seed) 可完整复现, 不同 seed 得到不同初始解;</li>
     *   <li>单元无任何候选(candidateCount == 0)属数据不可行, 直接抛错而非返回 -1。</li>
     * </ul>
     */
    public static int[] random(SchedulingProblem p, Random rng) {
        Objects.requireNonNull(p, "problem");
        Objects.requireNonNull(rng, "rng");
        int n = p.getUnitCount();
        int[] choices = new int[n];
        for (int i = 0; i < n; i++) {
            SchedulingUnit unit = p.getUnit(i);
            if (unit.candidateCount() == 0) {
                throw new IllegalArgumentException(
                        "单元无任何可排候选, 无法生成随机初始解: unitIndex=" + i);
            }
            choices[i] = rng.nextInt(unit.candidateCount());
        }
        return choices;
    }

    /** 计算放入 cand 后的全局硬违反增量(见类注释口径) */
    private static int addedHardConflicts(
            SchedulingUnit unit, CandidateAssignment cand,
            Map<Long, Map<Long, Integer>> teacherSlot,
            Map<Long, Map<Long, Integer>> classSlot,
            Map<Long, Map<Long, Integer>> roomSlot,
            Map<Long, Map<Integer, Integer>> offeringDayCount) {

        int added = 0;
        long[] occSlots = cand.occupiedSlotIdsUnsafe();
        long teacherId = unit.getTeacherId();
        long classroomId = cand.getClassroomId();
        int day = cand.getDayOfWeek();

        Map<Long, Integer> teacherCounts = teacherSlot.get(teacherId);
        if (teacherCounts != null) {
            for (long slot : occSlots) {
                if (teacherCounts.containsKey(slot)) {
                    added++;
                }
            }
        }
        Map<Long, Integer> roomCounts = roomSlot.get(classroomId);
        if (roomCounts != null) {
            for (long slot : occSlots) {
                if (roomCounts.containsKey(slot)) {
                    added++;
                }
            }
        }
        for (long classId : unit.classIdsUnsafe()) {
            Map<Long, Integer> classCounts = classSlot.get(classId);
            if (classCounts == null) {
                continue;
            }
            for (long slot : occSlots) {
                if (classCounts.containsKey(slot)) {
                    added++;
                }
            }
        }
        Map<Integer, Integer> offeringDays = offeringDayCount.get(unit.getCourseOfferingId());
        if (offeringDays != null) {
            added += offeringDays.getOrDefault(day, 0);
        }
        return added;
    }

    /** 将 cand 的占用写入各计数结构 */
    private static void applyAdd(
            SchedulingUnit unit, CandidateAssignment cand,
            Map<Long, Map<Long, Integer>> teacherSlot,
            Map<Long, Map<Long, Integer>> classSlot,
            Map<Long, Map<Long, Integer>> roomSlot,
            Map<Long, Map<Integer, Integer>> offeringDayCount) {

        long[] occSlots = cand.occupiedSlotIdsUnsafe();
        incrementEach(teacherSlot, unit.getTeacherId(), occSlots);
        incrementEach(roomSlot, cand.getClassroomId(), occSlots);
        for (long classId : unit.classIdsUnsafe()) {
            incrementEach(classSlot, classId, occSlots);
        }
        offeringDayCount.computeIfAbsent(unit.getCourseOfferingId(), k -> new HashMap<>())
                .merge(cand.getDayOfWeek(), 1, Integer::sum);
    }

    private static void incrementEach(Map<Long, Map<Long, Integer>> resourceSlot, long resourceId, long[] slots) {
        Map<Long, Integer> counts = resourceSlot.computeIfAbsent(resourceId, k -> new HashMap<>());
        for (long slot : slots) {
            counts.merge(slot, 1, Integer::sum);
        }
    }
}
