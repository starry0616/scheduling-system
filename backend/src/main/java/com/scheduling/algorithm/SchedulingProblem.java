package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 排课问题模型(纯算法 POJO, V2.2 §5)。
 *
 * <p>由 Service 组装阶段一次性装配完成, 之后算法层只消费本对象:
 * <pre>
 *   units          : 全部排课单元(每个 unit 已预计算 candidates);
 *   timeSlots      : 全局时间段((dayOfWeek, period) 升序);
 *   teacherPreferences: 教师时间偏好 teacherId -> (timeSlotId -> 偏好级别);
 *                     级别: -1=不偏好(软约束 S1 记违反), 0=一般, 1=偏好; 缺失视为 0;
 *   6 个软约束权重 w1..w6 + 推导出的硬约束权重 W_hard。
 * </pre>
 *
 * <p>W_hard 推导(V2.2 §10/§11): 先逐条求 6 个软惩罚项的理论上界
 * (S1_max=U*Dm, S2_max=U*4, S3_max=125C, S4_max=10T, S5_max=125C, S6_max=U*Dm),
 * 得 SoftMax = Σ(wj * Sj_max), 再取 W_hard = floor(SoftMax) + 1。
 * 数学保证: W_hard &gt; SoftMax ⟹ 每消除 1 次硬违反至多增加 SoftMax 软惩罚,
 * 净收益恒大于 0 ⟹ 算法永远不会为减少软惩罚而增加硬违反,
 * 且 硬违反少的解能量必然更低(硬约束严格优先)。
 *
 * <p>符号: U=排课单元数, T=不同教师数, C=不同班级数, Dm=max(durationSlots)。
 */
public final class SchedulingProblem {

    // ---------- 输入数据 ----------

    private final List<SchedulingUnit> units;
    private final List<TimeSlotInfo> timeSlots;
    private final Map<Long, TimeSlotInfo> timeSlotById;
    private final Map<Long, Map<Long, Integer>> teacherPreferences;

    // ---------- 软约束权重 ----------

    private final int wTeacherPreference;
    private final int wCourseDistribution;
    private final int wStudentBalance;
    private final int wTeacherContinuous;
    private final int wStudentIdle;
    private final int wMorningEvening;

    // ---------- 推导量 ----------

    private final int daysPerWeek;
    private final int periodsPerDay;
    /** 实际存在的星期值集合(升序, 默认 1..5; S2 理想间隔与 S3 按此遍历) */
    private final List<Integer> dayValues;
    private final int maxDurationSlots;
    private final List<Long> distinctTeacherIds;
    private final List<Long> distinctClassIds;
    private final double hardWeight;   // W_hard = floor(SoftMax) + 1(整数值, 以 double 承载便于能量计算)

    public SchedulingProblem(List<SchedulingUnit> units,
                             List<TimeSlotInfo> timeSlots,
                             Map<Long, Map<Long, Integer>> teacherPreferences,
                             int wTeacherPreference,
                             int wCourseDistribution,
                             int wStudentBalance,
                             int wTeacherContinuous,
                             int wStudentIdle,
                             int wMorningEvening) {
        this.units = List.copyOf(Objects.requireNonNull(units, "units"));
        if (this.units.isEmpty()) {
            throw new IllegalArgumentException("排课问题为空: 没有可排课的开课单元");
        }
        for (SchedulingUnit unit : this.units) {
            if (!unit.hasCandidates()) {
                throw new IllegalArgumentException(
                        "排课数据不可行: 存在无可选(教室,时间)组合的排课单元, offeringId="
                                + unit.getCourseOfferingId() + ", sessionIndex=" + unit.getSessionIndex());
            }
        }

        this.timeSlots = List.copyOf(Objects.requireNonNull(timeSlots, "timeSlots"));
        if (this.timeSlots.isEmpty()) {
            throw new IllegalArgumentException("时间段列表为空");
        }
        Map<Long, TimeSlotInfo> byId = new LinkedHashMap<>();
        Set<Integer> days = new LinkedHashSet<>();
        int maxPeriod = 0;
        for (TimeSlotInfo slot : this.timeSlots) {
            byId.put(slot.getId(), slot);
            days.add(slot.getDayOfWeek());
            maxPeriod = Math.max(maxPeriod, slot.getPeriod());
        }
        this.timeSlotById = Map.copyOf(byId);
        List<Integer> sortedDays = new ArrayList<>(days);
        sortedDays.sort(Integer::compareTo);
        this.dayValues = List.copyOf(sortedDays);
        this.daysPerWeek = sortedDays.size();
        this.periodsPerDay = maxPeriod;

        this.teacherPreferences = teacherPreferences == null || teacherPreferences.isEmpty()
                ? Map.of()
                : Map.copyOf(teacherPreferences);

        this.wTeacherPreference = wTeacherPreference;
        this.wCourseDistribution = wCourseDistribution;
        this.wStudentBalance = wStudentBalance;
        this.wTeacherContinuous = wTeacherContinuous;
        this.wStudentIdle = wStudentIdle;
        this.wMorningEvening = wMorningEvening;

        // 维度量 U/T/C/Dm
        Set<Long> teachers = new LinkedHashSet<>();
        Set<Long> classes = new LinkedHashSet<>();
        int dm = 1;
        for (SchedulingUnit unit : this.units) {
            teachers.add(unit.getTeacherId());
            for (long classId : unit.getClassIds()) {
                classes.add(classId);
            }
            dm = Math.max(dm, unit.getDurationSlots());
        }
        this.distinctTeacherIds = List.copyOf(teachers);
        this.distinctClassIds = List.copyOf(classes);
        this.maxDurationSlots = dm;

        // W_hard = floor(SoftMax) + 1
        long u = this.units.size();
        long t = this.distinctTeacherIds.size();
        long c = this.distinctClassIds.size();
        long s1Max = u * dm;
        long s2Max = u * 4L;
        long s3Max = 125L * c;
        long s4Max = 10L * t;
        long s5Max = 125L * c;
        long s6Max = u * dm;
        double softMax = wTeacherPreference * (double) s1Max
                + wCourseDistribution * (double) s2Max
                + wStudentBalance * (double) s3Max
                + wTeacherContinuous * (double) s4Max
                + wStudentIdle * (double) s5Max
                + wMorningEvening * (double) s6Max;
        this.hardWeight = Math.floor(softMax) + 1.0;
    }

    // ---------- 维度信息 ----------

    public List<SchedulingUnit> getUnits() {
        return units;
    }

    public int getUnitCount() {
        return units.size();
    }

    public SchedulingUnit getUnit(int index) {
        return units.get(index);
    }

    public List<TimeSlotInfo> getTimeSlots() {
        return timeSlots;
    }

    public TimeSlotInfo getTimeSlot(long slotId) {
        return timeSlotById.get(slotId);
    }

    public int getDaysPerWeek() {
        return daysPerWeek;
    }

    /** 实际存在的星期值(升序, 默认 [1,2,3,4,5]); S2/S3 按此集合遍历 */
    public List<Integer> getDayValues() {
        return dayValues;
    }

    public int getPeriodsPerDay() {
        return periodsPerDay;
    }

    public int getMaxDurationSlots() {
        return maxDurationSlots;
    }

    public List<Long> getDistinctTeacherIds() {
        return distinctTeacherIds;
    }

    public List<Long> getDistinctClassIds() {
        return distinctClassIds;
    }

    public int getDistinctTeacherCount() {
        return distinctTeacherIds.size();
    }

    public int getDistinctClassCount() {
        return distinctClassIds.size();
    }

    /** 硬约束权重 W_hard = floor(SoftMax) + 1 */
    public double getHardWeight() {
        return hardWeight;
    }

    public long getHardWeightAsLong() {
        return (long) hardWeight;
    }

    // ---------- 权重 ----------

    public int getWTeacherPreference() {
        return wTeacherPreference;
    }

    public int getWCourseDistribution() {
        return wCourseDistribution;
    }

    public int getWStudentBalance() {
        return wStudentBalance;
    }

    public int getWTeacherContinuous() {
        return wTeacherContinuous;
    }

    public int getWStudentIdle() {
        return wStudentIdle;
    }

    public int getWMorningEvening() {
        return wMorningEvening;
    }

    // ---------- 教师偏好 ----------

    /**
     * 教师在某时间段的偏好级别: -1=不偏好 / 0=一般 / 1=偏好; 无配置记录视为 0(一般)。
     */
    public int teacherPreferenceLevel(long teacherId, long slotId) {
        Map<Long, Integer> teacherPrefs = teacherPreferences.get(teacherId);
        if (teacherPrefs == null) {
            return 0;
        }
        return teacherPrefs.getOrDefault(slotId, 0);
    }

    /** 组装阶段回读 SoftMax, 便于回填任务配置字段 */
    public double getSoftMax() {
        return hardWeight - 1.0;
    }

    /** 默认解: 每个 unit 选中其第 0 个候选(作为无法抽样时的兜底/初始参考) */
    public SchedulingSolution defaultSolution() {
        int[] choices = new int[units.size()];
        for (int i = 0; i < choices.length; i++) {
            choices[i] = 0;
        }
        return new SchedulingSolution(choices);
    }
}
