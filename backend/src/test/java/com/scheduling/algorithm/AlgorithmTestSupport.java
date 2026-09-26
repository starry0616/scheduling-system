package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 算法层单元测试支撑: 构造纯 POJO 的小规模排课问题。
 *
 * <p>默认世界: 5 天 x 5 大节 = 25 个时间段(slotId = (day-1)*5 + period),
 * 教室默认 NORMAL 容量 60(no.101 / no.102)。
 */
final class AlgorithmTestSupport {

    static final int DAYS = 5;
    static final int PERIODS = 5;

    /** 默认软约束权重(W1..W6, 与任务创建默认一致) */
    static final int W1 = 50;
    static final int W2 = 30;
    static final int W3 = 25;
    static final int W4 = 30;
    static final int W5 = 25;
    static final int W6 = 15;

    private AlgorithmTestSupport() {
    }

    /** 一周时间段: day=1..5, period=1..5, slotId = (day-1)*5 + period */
    static List<TimeSlotInfo> weekSlots() {
        List<TimeSlotInfo> slots = new ArrayList<>();
        for (int day = 1; day <= DAYS; day++) {
            for (int period = 1; period <= PERIODS; period++) {
                slots.add(new TimeSlotInfo(slotId(day, period), day, period));
            }
        }
        return slots;
    }

    static long slotId(int day, int period) {
        return (long) (day - 1) * PERIODS + period;
    }

    static List<RoomInfo> normalRooms() {
        return List.of(new RoomInfo(101, 60, "NORMAL"), new RoomInfo(102, 60, "NORMAL"));
    }

    static List<RoomInfo> singleNormalRoom() {
        return List.of(new RoomInfo(101, 60, "NORMAL"));
    }

    /**
     * 构造一个排课单元(候选全部可用: 教师/班级/教室均无不可用时间)。
     */
    static SchedulingUnit unit(int unitIndex, long offeringId, int session, long teacherId, long classId,
                               int weeklySessions, int durationSlots, List<RoomInfo> rooms) {
        return unit(unitIndex, offeringId, session, teacherId, classId, weeklySessions,
                durationSlots, rooms, 40, "NORMAL", Set.of(), Set.of(), Map.of());
    }

    static SchedulingUnit unit(int unitIndex, long offeringId, int session, long teacherId, long classId,
                               int weeklySessions, int durationSlots, List<RoomInfo> rooms,
                               int totalStudents, String roomType,
                               Set<Long> teacherUnavailable, Set<Long> classUnavailable,
                               Map<Long, Set<Long>> classroomUnavailable) {
        List<CandidateAssignment> candidates = CandidateBuilder.buildCandidates(
                rooms, weekSlots(), totalStudents, roomType, durationSlots,
                teacherUnavailable, classUnavailable, classroomUnavailable);
        return new SchedulingUnit(unitIndex, offeringId, session, teacherId,
                new long[]{classId}, totalStudents, roomType, weeklySessions,
                durationSlots, candidates);
    }

    /** 查找某 unit 候选中 (教室, 起始时间段) 的下标; 找不到返回 -1 */
    static int candidateIndex(SchedulingUnit unit, long classroomId, long startSlotId) {
        List<CandidateAssignment> candidates = unit.getCandidates();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateAssignment cand = candidates.get(i);
            if (cand.getClassroomId() == classroomId && cand.getStartTimeSlotId() == startSlotId) {
                return i;
            }
        }
        return -1;
    }

    /** 构建排课问题(默认权重, 无教师偏好) */
    static SchedulingProblem problem(List<SchedulingUnit> units) {
        return problem(units, Map.of());
    }

    static SchedulingProblem problem(List<SchedulingUnit> units,
                                     Map<Long, Map<Long, Integer>> teacherPreferences) {
        return new SchedulingProblem(units, weekSlots(), teacherPreferences,
                W1, W2, W3, W4, W5, W6);
    }
}
