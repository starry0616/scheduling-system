package com.scheduling.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 候选生成器(纯算法函数, V2.2 §4)。
 *
 * <p>把一个 SchedulingUnit 的全部合法 (教室, 起始时间段) 组合预计算出来:
 * <pre>
 *   1. 教室维度过滤:
 *      - 容量 capacity >= totalStudents(满足 H4 容量);
 *      - 教室类型与课程要求类型严格一致(满足 H5 类型);
 *   2. 时间维度过滤(连续段):
 *      - 起始时间段所在同一天内, 存在 period 连续递增的 durationSlots 个大节
 *        (连续性基于 TimeSlot 的 (dayOfWeek, period), 禁止 slotId+1);
 *      - 教师、全部合班班级在这些占用时间段均可用(H 不可用时间预过滤);
 *      - 对每个教室: 这些占用时间段均不在该教室的不可用集合中。
 *   3. 产出笛卡尔积: 每个 (合法教室 x 合法起始连续段) 一个候选。
 * </pre>
 *
 * <p>由于不可用时间/容量/类型在候选构建阶段已被排除, 后续算法层只需从候选列表中取值,
 * 不会再产生"不可用时间上课""容量不足""类型不符"等结构性违反。
 */
public final class CandidateBuilder {

    private CandidateBuilder() {
    }

    /**
     * 教室类型匹配规则: 严格一致(课程要求的类型必须有对应类型的教室)。
     *
     * @param requiredRoomType 课程要求类型(NORMAL/MULTIMEDIA/LAB)
     * @param actualRoomType   教室实际类型(NORMAL/MULTIMEDIA/LAB)
     */
    public static boolean roomTypeMatches(String requiredRoomType, String actualRoomType) {
        if (requiredRoomType == null || actualRoomType == null) {
            return false;
        }
        return requiredRoomType.equals(actualRoomType);
    }

    /**
     * 生成某 SchedulingUnit 的全部合法候选。
     *
     * @param allRooms              可用教室池(RoomInfo, 顺序稳定)
     * @param timeSlots             系统时间段, 已按 (dayOfWeek, period) 升序
     * @param totalStudents         合班总人数(容量过滤)
     * @param requiredRoomType      课程要求教室类型
     * @param durationSlots         每次课连续占用的大节数
     * @param teacherUnavailable    教师不可用时间段 id 集合
     * @param classUnavailable      合班班级不可用时间段 id 并集
     * @param classroomUnavailable  教室不可用时间: classroomId -> 该教室不可用时间段 id 集合
     * @return 合法候选列表(顺序: 按时间连续段升序, 同段内按教室传入顺序); 为空表示该 Unit 数据不可行
     */
    public static List<CandidateAssignment> buildCandidates(
            List<RoomInfo> allRooms,
            List<TimeSlotInfo> timeSlots,
            int totalStudents,
            String requiredRoomType,
            int durationSlots,
            Set<Long> teacherUnavailable,
            Set<Long> classUnavailable,
            Map<Long, Set<Long>> classroomUnavailable) {

        Objects.requireNonNull(allRooms, "allRooms");
        Objects.requireNonNull(timeSlots, "timeSlots");
        Objects.requireNonNull(requiredRoomType, "requiredRoomType");
        Objects.requireNonNull(teacherUnavailable, "teacherUnavailable");
        Objects.requireNonNull(classUnavailable, "classUnavailable");
        Objects.requireNonNull(classroomUnavailable, "classroomUnavailable");

        if (allRooms.isEmpty() || timeSlots.isEmpty()) {
            return List.of();
        }

        // 1. 教室维度过滤(容量 + 类型)
        List<RoomInfo> validRooms = new ArrayList<>();
        for (RoomInfo room : allRooms) {
            if (room.capacity() >= totalStudents
                    && roomTypeMatches(requiredRoomType, room.roomType())) {
                validRooms.add(room);
            }
        }
        if (validRooms.isEmpty()) {
            return List.of();
        }

        List<CandidateAssignment> result = new ArrayList<>();

        // 2. 枚举所有合法起始连续段(按 timeSlots 顺序推进)
        for (int start = 0; start < timeSlots.size(); start++) {
            TimeSlotInfo first = timeSlots.get(start);

            // 检查能否以 first 为起点切出同一天 period 连续递增的 durationSlots 个 slot
            if (durationSlots > 1) {
                boolean consecutive = true;
                for (int step = 1; step < durationSlots; step++) {
                    int idx = start + step;
                    if (idx >= timeSlots.size()) {
                        consecutive = false;
                        break;
                    }
                    TimeSlotInfo prev = timeSlots.get(idx - 1);
                    TimeSlotInfo cur = timeSlots.get(idx);
                    if (cur.getDayOfWeek() != prev.getDayOfWeek()
                            || cur.getPeriod() != prev.getPeriod() + 1) {
                        consecutive = false;
                        break;
                    }
                }
                if (!consecutive) {
                    continue;
                }
            }

            long[] occupiedSlotIds = new long[durationSlots];
            int[] occupiedPeriods = new int[durationSlots];
            for (int step = 0; step < durationSlots; step++) {
                TimeSlotInfo slot = timeSlots.get(start + step);
                occupiedSlotIds[step] = slot.getId();
                occupiedPeriods[step] = slot.getPeriod();
            }

            // 时间维度: 教师/班级不可用时间预过滤
            if (intersectsAny(teacherUnavailable, occupiedSlotIds)) {
                continue;
            }
            if (intersectsAny(classUnavailable, occupiedSlotIds)) {
                continue;
            }

            // 3. 教室 x 起始连续段 笛卡尔积(逐教室校验该时间段是否可用)
            for (RoomInfo room : validRooms) {
                Set<Long> roomUnavailable = classroomUnavailable.get(room.id());
                if (roomUnavailable != null && !roomUnavailable.isEmpty()
                        && intersectsAny(roomUnavailable, occupiedSlotIds)) {
                    continue;
                }
                result.add(new CandidateAssignment(room.id(), first.getDayOfWeek(),
                        occupiedSlotIds, occupiedPeriods));
            }
        }

        return Collections.unmodifiableList(result);
    }

    /** occupied 序列中是否含 blocked 集合中的任意 slot id */
    private static boolean intersectsAny(Set<Long> blocked, long[] occupiedSlotIds) {
        for (long slotId : occupiedSlotIds) {
            if (blocked.contains(slotId)) {
                return true;
            }
        }
        return false;
    }
}
