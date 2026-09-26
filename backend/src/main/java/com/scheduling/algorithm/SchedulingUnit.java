package com.scheduling.algorithm;

import java.util.List;
import java.util.Objects;

/**
 * 排课单元(算法层纯 POJO, V2.2 §3)。
 *
 * <p>一个 SchedulingUnit = "某一开课实例(CourseOffering)的一次具体课次":
 * <pre>
 *   CourseOffering(每周 k 次课, 每次连续 durationSlots 个大节)
 *     → 按 weeklySessions 展开为 k 个 SchedulingUnit(sessionIndex = 0..k-1)
 *     → 每个 Unit 需要在 (教室, 起始时间段) 中选一个候选, 构成一次 Assignment
 * </pre>
 *
 * <p>语义约定(与 schema/V2.2 一致):
 * <ul>
 *   <li>classIds: 合班教学的班级 id 集合; totalStudents = 各班级人数之和, 用于候选教室容量过滤;</li>
 *   <li>requiredRoomType: 课程要求的教室类型(NORMAL/MULTIMEDIA/LAB), 候选生成时严格匹配;</li>
 *   <li>durationSlots: 每次课连续占用的大节数(理论课=1, 实验课=2); 连续占用基于
 *       TimeSlot 的 (dayOfWeek, period), 禁止用 slotId+1 推断;</li>
 *   <li>candidates: 该 Unit 全部合法 (教室, 起始时间段) 组合(含占用时间段序列), 由外部组装阶段预计算;</li>
 *   <li>isLabCourse = durationSlots &gt; 1(与实体派生规则一致)。</li>
 * </ul>
 *
 * <p>本类及其关联算法类绝不依赖 JPA/Spring/数据库, 仅消费组装阶段传入的已物化数据。
 */
public final class SchedulingUnit {

    /** 在 problem.units 中的下标(0-based, 稳定) */
    private final int unitIndex;

    /** 所属开课实例 id(course_offering.id) */
    private final long courseOfferingId;

    /** 该 offering 展开出的第几次课(0-based); 同 offering 的多个 Unit 共享同一 offeringId */
    private final int sessionIndex;

    /** 授课教师 id(teacher.id) */
    private final long teacherId;

    /** 覆盖班级 id(class.id), 合班时可能多个; 排序稳定 */
    private final long[] classIds;

    /** 合班总人数 = 各班级人数之和(候选教室容量过滤用) */
    private final int totalStudents;

    /** 课程要求的教室类型: NORMAL / MULTIMEDIA / LAB */
    private final String requiredRoomType;

    /** 所属 offering 每周课次数(用于 H7 同课次不同天约束的上下文) */
    private final int weeklySessions;

    /** 每次课连续占用的大节数(理论课=1, 实验课=2) */
    private final int durationSlots;

    /** 该 Unit 全部合法 (教室, 起始时间段) 组合, 按生成顺序稳定 */
    private final List<CandidateAssignment> candidates;

    public SchedulingUnit(int unitIndex, long courseOfferingId, int sessionIndex, long teacherId,
                          long[] classIds, int totalStudents, String requiredRoomType,
                          int weeklySessions, int durationSlots, List<CandidateAssignment> candidates) {
        if (unitIndex < 0) {
            throw new IllegalArgumentException("unitIndex 不能为负: " + unitIndex);
        }
        if (sessionIndex < 0) {
            throw new IllegalArgumentException("sessionIndex 不能为负: " + sessionIndex);
        }
        if (weeklySessions < 1) {
            throw new IllegalArgumentException("weeklySessions 必须 >= 1: " + weeklySessions);
        }
        if (durationSlots < 1) {
            throw new IllegalArgumentException("durationSlots 必须 >= 1: " + durationSlots);
        }
        Objects.requireNonNull(candidates, "candidates 不能为 null");
        this.unitIndex = unitIndex;
        this.courseOfferingId = courseOfferingId;
        this.sessionIndex = sessionIndex;
        this.teacherId = teacherId;
        this.classIds = classIds == null ? new long[0] : classIds.clone();
        this.totalStudents = totalStudents;
        this.requiredRoomType = requiredRoomType;
        this.weeklySessions = weeklySessions;
        this.durationSlots = durationSlots;
        this.candidates = List.copyOf(candidates);
    }

    public int getUnitIndex() {
        return unitIndex;
    }

    public long getCourseOfferingId() {
        return courseOfferingId;
    }

    public int getSessionIndex() {
        return sessionIndex;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public long[] getClassIds() {
        return classIds.clone();
    }

    /** 供同包算法类直接读取, 避免热路径拷贝 */
    long[] classIdsUnsafe() {
        return classIds;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public String getRequiredRoomType() {
        return requiredRoomType;
    }

    public int getWeeklySessions() {
        return weeklySessions;
    }

    public int getDurationSlots() {
        return durationSlots;
    }

    /** 是否实验课(= 每次课连续占用 > 1 个大节) */
    public boolean isLabCourse() {
        return durationSlots > 1;
    }

    public List<CandidateAssignment> getCandidates() {
        return candidates;
    }

    /** 是否存在可排候选(空 = 该 Unit 数据不可行, 组装/执行阶段据此提前报错) */
    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public int candidateCount() {
        return candidates.size();
    }

    @Override
    public String toString() {
        return "SchedulingUnit{unitIndex=" + unitIndex
                + ", offeringId=" + courseOfferingId
                + ", sessionIndex=" + sessionIndex
                + ", teacherId=" + teacherId
                + ", durationSlots=" + durationSlots
                + ", candidates=" + candidates.size() + "}";
    }
}
