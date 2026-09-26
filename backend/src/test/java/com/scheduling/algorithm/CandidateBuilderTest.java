package com.scheduling.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.scheduling.algorithm.AlgorithmTestSupport.slotId;
import static com.scheduling.algorithm.AlgorithmTestSupport.unit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CandidateBuilder 候选生成规则测试:
 * 容量/教室类型/教师与班级与教室不可用时间过滤, 以及实验课(2 大节)连续段判定。
 */
class CandidateBuilderTest {

    @Test
    void 教室容量不足的教室不会进入候选() {
        List<RoomInfo> rooms = List.of(
                new RoomInfo(101, 30, "NORMAL"),   // 容量不足(学生 40)
                new RoomInfo(102, 60, "NORMAL"));  // 容量足够
        SchedulingUnit u = unit(0, 1, 0, 10, 1, 1, 1, rooms);
        assertTrue(u.hasCandidates());
        for (CandidateAssignment cand : u.getCandidates()) {
            assertEquals(102, cand.getClassroomId(), "容量不足的教室 101 不应进入候选");
        }
    }

    @Test
    void 教室类型严格匹配() {
        // 课程要求 MULTIMEDIA, 池里只有 NORMAL/LAB => 无候选
        List<RoomInfo> rooms = List.of(
                new RoomInfo(101, 60, "NORMAL"),
                new RoomInfo(201, 60, "LAB"));
        SchedulingUnit u = unit(0, 1, 0, 10, 1, 1, 1, rooms,
                40, "MULTIMEDIA", Set.of(), Set.of(), Map.of());
        assertFalse(u.hasCandidates());

        // 课程要求 LAB, 池里有 LAB => 只有 LAB 教室进入候选
        List<RoomInfo> labRooms = List.of(
                new RoomInfo(101, 60, "NORMAL"),
                new RoomInfo(201, 60, "LAB"));
        SchedulingUnit lab = unit(0, 2, 0, 20, 2, 1, 2, labRooms,
                40, "LAB", Set.of(), Set.of(), Map.of());
        assertTrue(lab.hasCandidates());
        for (CandidateAssignment cand : lab.getCandidates()) {
            assertEquals(201, cand.getClassroomId(), "实验课只允许 LAB 教室");
        }
    }

    @Test
    void 教师不可用时间被排除() {
        Set<Long> teacherUnavailable = Set.of(slotId(1, 3));
        SchedulingUnit u = unit(0, 1, 0, 10, 1, 1, 1,
                List.of(new RoomInfo(101, 60, "NORMAL")), 40, "NORMAL",
                teacherUnavailable, Set.of(), Map.of());
        assertTrue(u.hasCandidates());
        for (CandidateAssignment cand : u.getCandidates()) {
            assertFalse(cand.overlapsSlot(slotId(1, 3)), "教师不可用时间段不应出现");
        }
    }

    @Test
    void 班级不可用时间被排除() {
        Set<Long> classUnavailable = Set.of(slotId(2, 4));
        SchedulingUnit u = unit(0, 1, 0, 10, 1, 1, 1,
                List.of(new RoomInfo(101, 60, "NORMAL")), 40, "NORMAL",
                Set.of(), classUnavailable, Map.of());
        assertTrue(u.hasCandidates());
        for (CandidateAssignment cand : u.getCandidates()) {
            assertFalse(cand.overlapsSlot(slotId(2, 4)));
        }
    }

    @Test
    void 教室不可用时间被排除且只影响该教室() {
        Map<Long, Set<Long>> roomUnavailable = Map.of(101L, Set.of(slotId(1, 3)));
        SchedulingUnit u = unit(0, 1, 0, 10, 1, 1, 1,
                List.of(new RoomInfo(101, 60, "NORMAL"), new RoomInfo(102, 60, "NORMAL")),
                40, "NORMAL", Set.of(), Set.of(), roomUnavailable);
        assertTrue(u.hasCandidates());
        for (CandidateAssignment cand : u.getCandidates()) {
            if (cand.getClassroomId() == 101) {
                assertFalse(cand.overlapsSlot(slotId(1, 3)), "教室 101 的不可用时间应被排除");
            }
        }
        // 同时间段 102 教室仍然可排
        SchedulingUnit unit2 = unit(0, 1, 0, 10, 1, 1, 1,
                List.of(new RoomInfo(101, 60, "NORMAL"), new RoomInfo(102, 60, "NORMAL")),
                40, "NORMAL", Set.of(), Set.of(), Map.of());
        assertTrue(AlgorithmTestSupport.candidateIndex(unit2, 102, slotId(1, 3)) >= 0);
    }

    @Test
    void 实验课两个大节必须同日连续且不跨天() {
        SchedulingUnit lab = unit(0, 1, 0, 10, 1, 1, 2,
                List.of(new RoomInfo(101, 60, "NORMAL")), 40, "NORMAL",
                Set.of(), Set.of(), Map.of());
        assertTrue(lab.hasCandidates());
        for (CandidateAssignment cand : lab.getCandidates()) {
            assertEquals(2, cand.getDurationSlots());
            int day = cand.getDayOfWeek();
            int[] periods = cand.occupiedPeriodsUnsafe();
            assertEquals(2, periods.length);
            assertEquals(periods[0] + 1, periods[1], "两个大节必须连续");
            assertEquals(day, cand.getDayOfWeek());
            assertTrue(periods[1] <= AlgorithmTestSupport.PERIODS, "不允许跨到第 6 大节");
        }
        // 每天最多可从第 1..4 大节开始(第 5 大节开始放不下 2 大节)
        long startSlotCount = lab.getCandidates().stream()
                .filter(c -> c.getDayOfWeek() == 1)
                .map(CandidateAssignment::getStartTimeSlotId)
                .distinct()
                .count();
        assertEquals(4, startSlotCount);
    }

    @Test
    void 教师班级教室全不可用则候选为空() {
        Set<Long> all = Set.of(slotId(1, 1), slotId(1, 2), slotId(1, 3), slotId(1, 4), slotId(1, 5),
                slotId(2, 1), slotId(2, 2), slotId(2, 3), slotId(2, 4), slotId(2, 5),
                slotId(3, 1), slotId(3, 2), slotId(3, 3), slotId(3, 4), slotId(3, 5),
                slotId(4, 1), slotId(4, 2), slotId(4, 3), slotId(4, 4), slotId(4, 5),
                slotId(5, 1), slotId(5, 2), slotId(5, 3), slotId(5, 4), slotId(5, 5));
        SchedulingUnit u = unit(0, 1, 0, 10, 1, 1, 1,
                List.of(new RoomInfo(101, 60, "NORMAL")), 40, "NORMAL",
                all, all, Map.of(101L, all));
        assertFalse(u.hasCandidates());
    }
}
