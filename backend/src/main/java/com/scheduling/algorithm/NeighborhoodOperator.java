package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 邻域算子(纯算法, V2.2 §14)。
 *
 * <p>三种加权邻域操作, 按概率选择:
 * <pre>
 *   MOVE(40%)       : 随机选一个 unit, 换到其候选列表中的另一个候选(整段换, 教室+时间一起);
 *   SWAP(30%)       : 随机选两个 durationSlots 相同的 unit, 交换两者当前安排(教室+起始时间);
 *   CHANGE_ROOM(30%): 随机选一个 unit, 在其候选列表中挑"起始时间相同、教室不同"的候选, 仅换教室。
 * </pre>
 *
 * <p>所有操作只从 unit 自身候选列表中取值, 因此每次邻域移动生成的 Assignment
 * 天然满足 容量(H4)/类型(H5)/不可用时间 约束。
 *
 * <p>SWAP 的合法性: 由于候选列表是按 unit 独立预过滤的(教师/班级不可用时间不同),
 * 只有 A 的候选包含 B 当前(教室, 时间)且 B 的候选包含 A 当前(教室, 时间)时才执行交换,
 * 保证交换后双方 Assignment 仍然合法; 找不到这样的 B 时本次 SWAP 失败(由引擎重试)。
 */
public final class NeighborhoodOperator {

    private static final double MOVE_RATIO = 0.40;
    private static final double SWAP_RATIO = 0.70;

    private final Random rng;

    public NeighborhoodOperator(Random rng) {
        this.rng = rng;
    }

    /**
     * 按概率随机应用一次邻域操作(就地修改 sol)。
     *
     * @return true=已产生有效移动; false=本次操作不可行(引擎应换一种操作重试)
     */
    public boolean apply(SchedulingProblem p, int[] sol) {
        double r = rng.nextDouble();
        if (r < MOVE_RATIO) {
            return move(p, sol);
        }
        if (r < SWAP_RATIO) {
            return swap(p, sol);
        }
        return changeRoom(p, sol);
    }

    // ---------- MOVE: 随机 unit 换到另一候选 ----------

    private boolean move(SchedulingProblem p, int[] sol) {
        int n = p.getUnitCount();
        // 找一个候选数 > 1 的 unit(从 n 次随机尝试中取第一个满足的, 控制偏置)
        for (int attempt = 0; attempt < 3 * n + 1; attempt++) {
            int i = rng.nextInt(n);
            SchedulingUnit unit = p.getUnit(i);
            int size = unit.candidateCount();
            if (size <= 1) {
                continue;
            }
            int cur = sol[i];
            // 在 size-1 个"其它候选"中等概率挑一个
            int offset = rng.nextInt(size - 1);
            int target = offset < cur ? offset : offset + 1;
            sol[i] = target;
            return true;
        }
        return false;
    }

    // ---------- SWAP: 同 durationSlots 的两个 unit 交换安排 ----------

    private boolean swap(SchedulingProblem p, int[] sol) {
        int n = p.getUnitCount();
        for (int attempt = 0; attempt < 3 * n + 1; attempt++) {
            int a = rng.nextInt(n);
            SchedulingUnit ua = p.getUnit(a);
            CandidateAssignment candA = ua.getCandidates().get(sol[a]);

            // 收集与该 unit 同 durationSlots 且可交叉交换的伙伴(候选列表按 unit 独立预过滤,
            // 必须双方候选均包含对方的 (教室,起始时间) 才能保证交换后仍合法)
            List<Integer> partners = new ArrayList<>();
            for (int b = 0; b < n; b++) {
                if (b == a) {
                    continue;
                }
                SchedulingUnit ub = p.getUnit(b);
                if (ub.getDurationSlots() != ua.getDurationSlots()) {
                    continue;
                }
                CandidateAssignment candB = ub.getCandidates().get(sol[b]);
                if (findCandidateIndex(ua, candB.getClassroomId(), candB.getStartTimeSlotId()) >= 0
                        && findCandidateIndex(ub, candA.getClassroomId(), candA.getStartTimeSlotId()) >= 0) {
                    partners.add(b);
                }
            }
            if (partners.isEmpty()) {
                continue;   // 换一个 A 再试
            }
            int b = partners.get(rng.nextInt(partners.size()));
            SchedulingUnit ub = p.getUnit(b);
            CandidateAssignment candB = ub.getCandidates().get(sol[b]);
            int aNew = findCandidateIndex(ua, candB.getClassroomId(), candB.getStartTimeSlotId());
            int bNew = findCandidateIndex(ub, candA.getClassroomId(), candA.getStartTimeSlotId());
            sol[a] = aNew;
            sol[b] = bNew;
            return true;
        }
        return false;
    }

    // ---------- CHANGE_ROOM: 同起始时间仅换教室 ----------

    private boolean changeRoom(SchedulingProblem p, int[] sol) {
        int n = p.getUnitCount();
        for (int attempt = 0; attempt < 3 * n + 1; attempt++) {
            int i = rng.nextInt(n);
            SchedulingUnit unit = p.getUnit(i);
            CandidateAssignment cur = unit.getCandidates().get(sol[i]);

            List<Integer> sameStartOthers = new ArrayList<>();
            for (int ci = 0; ci < unit.candidateCount(); ci++) {
                CandidateAssignment cand = unit.getCandidates().get(ci);
                if (cand.getClassroomId() != cur.getClassroomId()
                        && cand.getStartTimeSlotId() == cur.getStartTimeSlotId()) {
                    sameStartOthers.add(ci);
                }
            }
            if (sameStartOthers.isEmpty()) {
                continue;
            }
            sol[i] = sameStartOthers.get(rng.nextInt(sameStartOthers.size()));
            return true;
        }
        return false;
    }

    /** 在某 unit 候选列表中查找指定 (教室, 起始时间) 的候选下标; 不存在返回 -1 */
    private static int findCandidateIndex(SchedulingUnit unit, long classroomId, long startTimeSlotId) {
        List<CandidateAssignment> candidates = unit.getCandidates();
        for (int ci = 0; ci < candidates.size(); ci++) {
            CandidateAssignment cand = candidates.get(ci);
            if (cand.getClassroomId() == classroomId
                    && cand.getStartTimeSlotId() == startTimeSlotId) {
                return ci;
            }
        }
        return -1;
    }
}
