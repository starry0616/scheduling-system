package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.CandidateAssignment;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SchedulingUnit;
import com.scheduling.algorithm.TimeSlotInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段8.2-B 标准实验数据集测试(纯内存, 快速完成, 不落库)。
 *
 * <p>覆盖: 三规模创建 / 同 seed 完全一致 / 不同 seed 产生不同数据 /
 * candidateCount &gt; 0 / 引用完整 / durationSlots=2 无跨天候选 /
 * DatasetSummary 数值正确 / SMALL 可行性 sanity。
 */
class DatasetFactoryTest {

    private static final long SEED = 20260901L;

    // ---------- 1/2/3: 三规模均可创建 ----------

    @Test
    void small数据集可创建() {
        ExperimentDataset ds = DatasetFactory.dataset(ExperimentDatasetSize.SMALL, SEED);
        assertNotNull(ds.problem());
        assertNotNull(ds.summary());
        assertTrue(ds.summary().schedulingUnitCount() > 0);
        assertTrue(ds.problem().getUnitCount() == ds.summary().schedulingUnitCount());
        System.out.println("[SMALL] " + ds.summary());
    }

    @Test
    void medium数据集可创建() {
        ExperimentDataset ds = DatasetFactory.dataset(ExperimentDatasetSize.MEDIUM, SEED);
        assertNotNull(ds.problem());
        assertTrue(ds.summary().schedulingUnitCount() > 0);
        System.out.println("[MEDIUM] " + ds.summary());
    }

    @Test
    void large数据集可创建() {
        ExperimentDataset ds = DatasetFactory.dataset(ExperimentDatasetSize.LARGE, SEED);
        assertNotNull(ds.problem());
        assertTrue(ds.summary().schedulingUnitCount() > 0);
        System.out.println("[LARGE] " + ds.summary());
    }

    // ---------- 4: 同 seed 完全一致 ----------

    @Test
    void 同seed生成完全一致() {
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            ExperimentDataset a = DatasetFactory.dataset(size, SEED);
            ExperimentDataset b = DatasetFactory.dataset(size, SEED);
            assertEquals(a.summary(), b.summary(),
                    "同 seed 摘要必须一致: " + size);
            assertEquals(snapshot(a.problem()), snapshot(b.problem()),
                    "同 seed 数据内容必须逐位一致: " + size);
        }
    }

    // ---------- 5: 不同 seed 可以产生不同数据 ----------

    @Test
    void 不同seed产生不同数据() {
        ExperimentDataset a = DatasetFactory.dataset(ExperimentDatasetSize.SMALL, SEED);
        ExperimentDataset b = DatasetFactory.dataset(ExperimentDatasetSize.SMALL, SEED + 1);
        assertTrue(a.summary().schedulingUnitCount() > 0);
        assertTrue(b.summary().schedulingUnitCount() > 0);
        assertNotEquals(snapshot(a.problem()), snapshot(b.problem()),
                "不同 seed 应产生不同课程/偏好/候选数据");
    }

    // ---------- 6: candidateCount 全部 > 0 ----------

    @Test
    void 所有unit候选数均大于0() {
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            SchedulingProblem p = DatasetFactory.create(size, SEED);
            for (SchedulingUnit unit : p.getUnits()) {
                assertTrue(unit.candidateCount() > 0,
                        "unit 候选数必须 > 0: " + size + ", unit=" + unit);
            }
        }
    }

    // ---------- 7: 数据引用完整 ----------

    @Test
    void 数据引用完整无悬空() {
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            SchedulingProblem p = DatasetFactory.create(size, SEED);
            DatasetDesign design = DatasetFactory.designOf(size);

            assertEquals(design.teacherCount(), p.getDistinctTeacherCount(),
                    "教师数必须与设计一致: " + size);
            assertEquals(design.classCount(), p.getDistinctClassCount(),
                    "班级数必须与设计一致: " + size);

            Set<Long> teacherPool = prefixRange(1000L, design.teacherCount());
            Set<Long> classPool = prefixRange(2000L, design.classCount());
            Set<Long> roomPool = prefixRange(3001L, design.classroomCount());

            for (SchedulingUnit unit : p.getUnits()) {
                assertTrue(teacherPool.contains(unit.getTeacherId()),
                        "teacherId 越界: " + unit.getTeacherId() + " (" + size + ")");
                long[] classIds = unit.getClassIds();
                assertTrue(classIds.length >= 1, "unit 必须至少覆盖 1 个班级");
                for (long classId : classIds) {
                    assertTrue(classPool.contains(classId),
                            "classId 越界: " + classId + " (" + size + ")");
                }
                for (CandidateAssignment cand : unit.getCandidates()) {
                    assertTrue(roomPool.contains(cand.getClassroomId()),
                            "classroomId 越界: " + cand.getClassroomId() + " (" + size + ")");
                }
            }
            assertTrue(prefixRange(4000L, design.courseOfferingCount())
                            .containsAll(distinctOfferingIds(p)),
                    "offeringId 引用不完整: " + size);

            // 时间槽: 25 个, 均为 1..25, 且 candidate 引用的槽全部存在
            List<TimeSlotInfo> slots = p.getTimeSlots();
            assertEquals(25, slots.size(), "时间槽必须是系统 25 个: " + size);
            for (SchedulingUnit unit : p.getUnits()) {
                for (CandidateAssignment cand : unit.getCandidates()) {
                    for (long slotId : cand.getOccupiedSlotIds()) {
                        assertNotNull(p.getTimeSlot(slotId),
                                "candidate 引用了不存在的槽: " + slotId + " (" + size + ")");
                    }
                }
            }
            // 教室全部被使用(每类型教室对任意同类课程均可用, 类型均至少 1 门课)
            Set<Long> usedRooms = new LinkedHashSet<>();
            for (SchedulingUnit unit : p.getUnits()) {
                for (CandidateAssignment cand : unit.getCandidates()) {
                    usedRooms.add(cand.getClassroomId());
                }
            }
            assertEquals(design.classroomCount(), usedRooms.size(),
                    "设计教室池中的教室必须全部投入使用: " + size);
        }
    }

    // ---------- 8: durationSlots=2 无跨天候选 ----------

    @Test
    void durationSlots为2的课程无跨天候选() {
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            SchedulingProblem p = DatasetFactory.create(size, SEED);
            int checked = 0;
            for (SchedulingUnit unit : p.getUnits()) {
                if (unit.getDurationSlots() < 2) {
                    continue;
                }
                for (CandidateAssignment cand : unit.getCandidates()) {
                    long[] slotIds = cand.getOccupiedSlotIds();
                    assertEquals(2, slotIds.length, "durationSlots=2 必须占用 2 个槽");
                    TimeSlotInfo first = p.getTimeSlot(slotIds[0]);
                    TimeSlotInfo second = p.getTimeSlot(slotIds[1]);
                    assertNotNull(first);
                    assertNotNull(second);
                    assertEquals(first.getDayOfWeek(), second.getDayOfWeek(),
                            "durationSlots=2 不允许跨天: " + size + ", cand=" + cand);
                    assertEquals(first.getPeriod() + 1, second.getPeriod(),
                            "durationSlots=2 必须同日 period 连续递增: " + size + ", cand=" + cand);
                    checked++;
                }
            }
            assertTrue(checked > 0, "每个规模都应存在实验课候选: " + size);
        }
    }

    // ---------- 9: DatasetSummary 数值正确 ----------

    @Test
    void summary数值正确() {
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            ExperimentDataset ds = DatasetFactory.dataset(size, SEED);
            SchedulingProblem p = ds.problem();
            DatasetSummary s = ds.summary();
            DatasetDesign design = DatasetFactory.designOf(size);

            assertEquals(size.name(), s.datasetSize());
            assertEquals(SEED, s.seed());
            assertEquals(design.teacherCount(), s.teacherCount());
            assertEquals(p.getDistinctTeacherCount(), s.teacherCount());
            assertEquals(design.classCount(), s.classCount());
            assertEquals(p.getDistinctClassCount(), s.classCount());
            assertEquals(design.classroomCount(), s.classroomCount());
            assertEquals(design.courseOfferingCount(), s.courseOfferingCount());
            assertEquals(distinctOfferingIds(p).size(), s.courseOfferingCount());
            assertEquals(p.getUnitCount(), s.schedulingUnitCount());
            assertEquals(p.getTimeSlots().size(), s.timeslotCount());
            assertEquals(25, s.timeslotCount());

            long total = 0;
            long min = Long.MAX_VALUE;
            long max = 0;
            for (SchedulingUnit unit : p.getUnits()) {
                long cnt = unit.candidateCount();
                total += cnt;
                min = Math.min(min, cnt);
                max = Math.max(max, cnt);
            }
            assertEquals(total, s.totalCandidateCount());
            assertEquals((double) total / p.getUnitCount(), s.averageCandidateCount(), 1e-9);
            assertEquals((int) min, s.minCandidateCount());
            assertEquals((int) max, s.maxCandidateCount());
            assertTrue(s.minCandidateCount() > 0);
        }
    }

    // ---------- 10: 可行性 sanity(标准数据集存在可行排课) ----------

    @Test
    void small数据集存在可行排课_构造性验证() {
        SchedulingProblem p = DatasetFactory.create(ExperimentDatasetSize.SMALL, SEED);
        // 确定性随机次序首适应(固定 restart 序列): 若任一顺序能全部无冲突着色,
        // 即构造性地证明存在可行排课。负载强裕量下数千次首适应尝试毫秒级完成。
        assertTrue(randomizedFirstFit(p, 400),
                "SMALL 标准数据集必须存在无硬约束冲突的可行排课(确定性随机次序构造验证)");
    }

    // ---------- helpers ----------

    /**
     * 构造性可行性: 依次尝试固定序列的随机 unit 次序 + 首适应候选,
     * 若某种顺序把全部 unit 无冲突着色, 即证明该实例存在可行排课。
     */
    private static boolean randomizedFirstFit(SchedulingProblem p, int restarts) {
        for (int attempt = 1; attempt <= restarts; attempt++) {
            List<SchedulingUnit> order = new ArrayList<>(p.getUnits());
            java.util.Collections.shuffle(order, new java.util.Random(2_026_000_000L + attempt));
            if (colorAllUnits(order)) {
                return true;
            }
        }
        return false;
    }

    private static boolean colorAllUnits(List<SchedulingUnit> order) {
        Set<String> used = new LinkedHashSet<>();
        for (SchedulingUnit unit : order) {
            boolean placed = false;
            for (CandidateAssignment cand : unit.getCandidates()) {
                List<String> keys = candidateKeys(unit, cand);
                boolean ok = true;
                for (String key : keys) {
                    if (used.contains(key)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    used.addAll(keys);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    private static List<String> candidateKeys(SchedulingUnit unit, CandidateAssignment cand) {
        List<String> keys = new ArrayList<>();
        for (long slotId : cand.getOccupiedSlotIds()) {
            keys.add("r" + cand.getClassroomId() + ":" + slotId);
            keys.add("t" + unit.getTeacherId() + ":" + slotId);
            for (long classId : unit.getClassIds()) {
                keys.add("c" + classId + ":" + slotId);
            }
            keys.add("o" + unit.getCourseOfferingId() + ":" + cand.getDayOfWeek());
        }
        return keys;
    }

    /** 规范化快照: 内容按稳定顺序逐行序列化, 用于同 seed/异 seed 比较 */
    private static List<String> snapshot(SchedulingProblem p) {
        List<String> lines = new ArrayList<>();
        for (SchedulingUnit unit : p.getUnits()) {
            StringBuilder sb = new StringBuilder();
            sb.append("U:").append(unit.getTeacherId())
                    .append(":O:").append(unit.getCourseOfferingId())
                    .append(":S:").append(unit.getSessionIndex())
                    .append(":C:").append(Arrays.toString(unit.getClassIds()))
                    .append(":W:").append(unit.getWeeklySessions())
                    .append(":D:").append(unit.getDurationSlots())
                    .append(":T:").append(unit.getRequiredRoomType())
                    .append(":N:").append(unit.getTotalStudents())
                    .append(":K:").append(unit.candidateCount());
            for (CandidateAssignment cand : unit.getCandidates()) {
                sb.append('|').append(cand.getClassroomId())
                        .append(':').append(cand.getDayOfWeek())
                        .append(':').append(Arrays.toString(cand.getOccupiedSlotIds()));
            }
            lines.add(sb.toString());
        }
        List<String> prefLines = new ArrayList<>();
        for (long teacherId : p.getDistinctTeacherIds()) {
            for (TimeSlotInfo slot : p.getTimeSlots()) {
                int level = p.teacherPreferenceLevel(teacherId, slot.getId());
                if (level != 0) {
                    prefLines.add("P:" + teacherId + ":" + slot.getId() + ":" + level);
                }
            }
        }
        prefLines.sort(Comparator.naturalOrder());
        lines.addAll(prefLines);
        return lines;
    }

    private static Set<Long> prefixRange(long start, int count) {
        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            ids.add(start + i);
        }
        return ids;
    }

    private static Set<Long> distinctOfferingIds(SchedulingProblem p) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SchedulingUnit unit : p.getUnits()) {
            ids.add(unit.getCourseOfferingId());
        }
        return ids;
    }
}
