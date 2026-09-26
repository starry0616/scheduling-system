package com.scheduling.algorithm;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 合法排课候选(V2.2 §4)。
 *
 * <p>表示"一个 SchedulingUnit 可选的 (教室, 起始时间段) 组合", 候选在组装阶段预计算:
 * <pre>
 *   - 教室: 容量足够 且 教室类型严格匹配课程要求 且 (教室, 每个占用时间段) 均可用;
 *   - 时间: 起始时间段所在连续段可容纳 durationSlots 个大节
 *           且 教师/全部合班班级在每个占用时间段均可用;
 *   - occupiedSlotIds: 该次课实际占用的全部 time_slot id(实验课含连续 2 个大节),
 *           所有硬/软约束一律基于 occupiedSlotIds 计算, 而非只看起始时间段。
 * </pre>
 *
 * <p>时间连续性判断依据 TimeSlot 的 (dayOfWeek, period): 同一天且 period 逐次 +1,
 * 禁止使用 slotId + 1 推断连续性。
 */
public final class CandidateAssignment {

    /** 教室 id(classroom.id) */
    private final long classroomId;

    /** 起始时间段所在星期(1=周一..5=周五) */
    private final int dayOfWeek;

    /** 占用的全部 time_slot id(按时间先后), 至少 1 个 */
    private final long[] occupiedSlotIds;

    /** 与 occupiedSlotIds 对齐的 period(同一天内, period 依次 +1) */
    private final int[] occupiedPeriods;

    public CandidateAssignment(long classroomId, int dayOfWeek,
                               List<Long> occupiedSlotIds, List<Integer> occupiedPeriods) {
        if (occupiedSlotIds == null || occupiedSlotIds.isEmpty()) {
            throw new IllegalArgumentException("occupiedSlotIds 不能为空");
        }
        if (occupiedPeriods == null || occupiedPeriods.size() != occupiedSlotIds.size()) {
            throw new IllegalArgumentException("occupiedPeriods 必须与 occupiedSlotIds 等长");
        }
        this.classroomId = classroomId;
        this.dayOfWeek = dayOfWeek;
        this.occupiedSlotIds = occupiedSlotIds.stream().mapToLong(Long::longValue).toArray();
        this.occupiedPeriods = occupiedPeriods.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 包内构造(候选由组装阶段的 CandidateBuilder 统一生成) */
    CandidateAssignment(long classroomId, int dayOfWeek, long[] occupiedSlotIds, int[] occupiedPeriods) {
        if (occupiedSlotIds == null || occupiedSlotIds.length == 0
                || occupiedPeriods == null || occupiedPeriods.length != occupiedSlotIds.length) {
            throw new IllegalArgumentException("occupiedSlotIds/occupiedPeriods 非法");
        }
        this.classroomId = classroomId;
        this.dayOfWeek = dayOfWeek;
        this.occupiedSlotIds = occupiedSlotIds.clone();
        this.occupiedPeriods = occupiedPeriods.clone();
    }

    public long getClassroomId() {
        return classroomId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    /** 起始时间段 id(占用序列的第 1 个) */
    public long getStartTimeSlotId() {
        return occupiedSlotIds[0];
    }

    /** 每次课占用的大节数 */
    public int getDurationSlots() {
        return occupiedSlotIds.length;
    }

    public long[] getOccupiedSlotIds() {
        return occupiedSlotIds.clone();
    }

    /** 同包算法类热路径直接读取, 避免拷贝 */
    long[] occupiedSlotIdsUnsafe() {
        return occupiedSlotIds;
    }

    /** 同包算法类热路径直接读取, 避免拷贝 */
    int[] occupiedPeriodsUnsafe() {
        return occupiedPeriods;
    }

    public boolean overlapsSlot(long slotId) {
        for (long id : occupiedSlotIds) {
            if (id == slotId) {
                return true;
            }
        }
        return false;
    }

    public boolean sameStartSlot(CandidateAssignment other) {
        return occupiedSlotIds[0] == other.occupiedSlotIds[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CandidateAssignment that)) {
            return false;
        }
        return classroomId == that.classroomId
                && Arrays.equals(occupiedSlotIds, that.occupiedSlotIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classroomId, Arrays.hashCode(occupiedSlotIds));
    }

    @Override
    public String toString() {
        return "Candidate{classroom=" + classroomId + ", day=" + dayOfWeek
                + ", startSlotId=" + getStartTimeSlotId() + ", occupied=" + occupiedSlotIds.length + "}";
    }
}
