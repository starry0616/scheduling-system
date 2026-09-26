package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.CandidateAssignment;
import com.scheduling.algorithm.CandidateBuilder;
import com.scheduling.algorithm.RoomInfo;
import com.scheduling.algorithm.SchedulingProblem;
import com.scheduling.algorithm.SchedulingUnit;
import com.scheduling.algorithm.TimeSlotInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 标准实验数据集工厂(阶段8.2-B, V2.2 §24.3)。
 *
 * <p>按真实业务语义纯内存生成 SchedulingProblem:
 * <pre>
 *   CourseOffering(教师 + 班级 + weeklySessions + durationSlots + 教室类型要求)
 *     → 按 weeklySessions 展开为 SchedulingUnit(sessionIndex=0..k-1)
 *     → 每个 Unit 的候选由 CandidateBuilder 按当前系统规则预计算(容量/教室类型/
 *       (dayOfWeek,period) 连续段; 数据生成不产生任何不可用时间)
 *     → 组装为 SchedulingProblem
 * </pre>
 *
 * <p>数据集语义(与实体/Schema 约定一致):
 * <ul>
 *   <li>时间轴 = 系统 25 个时间槽(day=1..5, period=1..5, slotId=(day-1)*5+period);</li>
 *   <li>durationSlots=1 理论课 / =2 实验课(连续占用同一天 period+1, isLabCourse 派生自 duration&gt;1);</li>
 *   <li>课程要求教室类型与教室池类型严格匹配(NORMAL/MULTIMEDIA/LAB), 容量按班级人数过滤;</li>
 *   <li>教师时间偏好仅作软约束输入(level -1/+1), 不产生硬性不可用;</li>
 *   <li>teacher/class/classroom 引用为自洽虚拟目录(前缀 1000/2000/3000), offeringId 前缀 4000, 无悬空引用。</li>
 * </ul>
 *
 * <p><b>结构性可行设计(保证标准数据集理论可行):</b>
 * 教室类型与课程类型一一对应, 因此可行性瓶颈是各类型教室的每周时段容量。工厂按
 * “最大可能课次负载 &le; 类型教室周容量(含裕量)”分配每个类型的开课实例配额,
 * 并把每周课次控制在一天容量内(理论课 1..4 次/周, 实验课 1..2 次/周), 班级/教师
 * 负载远低于 25 时段/周。SMALL 数据集由测试做精确构造性可行验证。
 *
 * <p>可重复性: 全部随机源来自单个 new Random(seed); 不依赖时间/UUID/线程随机。
 * 同一 (size, seed) 生成的结构与内容逐位一致。
 *
 * <p>冲突密度(教师共享程度/教室紧张度/偏好禁用比例/合班比例)作为后续场景矩阵参数,
 * 本阶段标准数据集采用确定性类型配额 + 均匀轮转(低冲突密度基线)。
 */
public final class DatasetFactory {

    private static final String ROOM_NORMAL = "NORMAL";
    private static final String ROOM_MULTIMEDIA = "MULTIMEDIA";
    private static final String ROOM_LAB = "LAB";

    private static final int DAYS = 5;
    private static final int PERIODS = 5;

    /** 与任务创建默认一致的软约束权重 */
    private static final int W1 = 50;
    private static final int W2 = 30;
    private static final int W3 = 25;
    private static final int W4 = 30;
    private static final int W5 = 25;
    private static final int W6 = 15;

    /** 每位教师生成 4 个不偏好 + 3 个偏好的时间槽(纯软约束难度, 不影响硬可行性) */
    private static final int PREF_NEGATIVE = 4;
    private static final int PREF_POSITIVE = 3;

    private DatasetFactory() {
    }

    /** 创建标准实验数据集, 返回 SchedulingProblem(可直接交给 SimulatedAnnealing.run) */
    public static SchedulingProblem create(ExperimentDatasetSize size, long seed) {
        return dataset(size, seed).problem();
    }

    /** 创建标准实验数据集, 返回 (problem + summary) */
    public static ExperimentDataset dataset(ExperimentDatasetSize size, long seed) {
        Profile p = profileOf(size);
        Random rng = new Random(seed);

        List<TimeSlotInfo> timeSlots = buildTimeSlots();
        List<RoomInfo> rooms = buildRooms(rng, p);

        List<Long> teacherIds = range(1000L, p.teachers);
        List<Long> classIds = range(2000L, p.classes);
        int[] classSizes = new int[p.classes];
        for (int i = 0; i < p.classes; i++) {
            classSizes[i] = 28 + rng.nextInt(13);   // 28..40(单班)
        }
        List<Long> rotatedTeachers = new ArrayList<>(teacherIds);
        Collections.shuffle(rotatedTeachers, rng);  // 轮转次序同样由 seed 决定

        // ---- 展开开课实例 → SchedulingUnit ----
        List<SchedulingUnit> units = new ArrayList<>();
        Set<Long> offeringIds = new LinkedHashSet<>();
        int unitIndex = 0;
        long totalCandidateCount = 0;
        int minCandidate = Integer.MAX_VALUE;
        int maxCandidate = 0;

        int totalOfferings = p.normalOfferings + p.multiOfferings + p.labOfferings;
        for (int oi = 0; oi < totalOfferings; oi++) {
            String roomType = typeOf(oi, p);
            long teacherId = rotatedTeachers.get(oi % p.teachers);   // 均匀轮转, 每位教师均被使用
            int durationSlots = ROOM_LAB.equals(roomType) ? 2 : 1;
            int weeklySessions = ROOM_LAB.equals(roomType)
                    ? 1 + rng.nextInt(2)          // 实验课 1..2 次/周
                    : theoryWeeklySessions(rng);  // 理论/多媒体课 1..4 次/周(≤ 5 天)
            int classIdx = oi % p.classes;                            // 轮转覆盖全部班级
            long[] offeringClassIds = new long[]{classIds.get(classIdx)};
            int totalStudents = classSizes[classIdx];

            List<CandidateAssignment> candidates = CandidateBuilder.buildCandidates(
                    rooms, timeSlots, totalStudents, roomType, durationSlots,
                    Set.of(), Set.of(), Map.of());
            if (candidates.isEmpty()) {
                // 数据生成自检: 不允许出现 candidateCount=0 的意外坏数据
                throw new IllegalStateException(
                        "数据集生成错误: offering 无任何合法候选, size=" + size
                                + ", seed=" + seed + ", offeringIndex=" + oi
                                + ", type=" + roomType + ", duration=" + durationSlots
                                + ", totalStudents=" + totalStudents);
            }

            long offeringId = 4000L + oi;
            offeringIds.add(offeringId);
            for (int session = 0; session < weeklySessions; session++) {
                SchedulingUnit unit = new SchedulingUnit(unitIndex++, offeringId, session,
                        teacherId, offeringClassIds, totalStudents, roomType,
                        weeklySessions, durationSlots, candidates);
                units.add(unit);
                int cnt = unit.candidateCount();
                totalCandidateCount += cnt;
                minCandidate = Math.min(minCandidate, cnt);
                maxCandidate = Math.max(maxCandidate, cnt);
            }
        }

        // ---- 教师时间偏好(软约束输入, 无硬性不可用) ----
        Map<Long, Map<Long, Integer>> preferences = buildTeacherPreferences(rng, teacherIds);

        SchedulingProblem problem = new SchedulingProblem(units, timeSlots, preferences,
                W1, W2, W3, W4, W5, W6);

        DatasetSummary summary = new DatasetSummary(
                size.name(),
                seed,
                teacherIds.size(),
                classIds.size(),
                offeringIds.size(),
                units.size(),
                rooms.size(),
                timeSlots.size(),
                totalCandidateCount,
                (double) totalCandidateCount / units.size(),
                minCandidate,
                maxCandidate);
        return new ExperimentDataset(problem, summary);
    }

    /** 读取某规模的配置设计值(供摘要校验与论文数据描述) */
    public static DatasetDesign designOf(ExperimentDatasetSize size) {
        Profile p = profileOf(size);
        return new DatasetDesign(p.teachers, p.classes,
                p.normalRooms + p.multiRooms + p.labRooms,
                p.normalOfferings + p.multiOfferings + p.labOfferings);
    }

    // ---------- 内部: 规模 profile ----------

    private record Profile(int teachers, int classes,
                           int normalRooms, int multiRooms, int labRooms,
                           int normalOfferings, int multiOfferings, int labOfferings) {
    }

    /**
     * 三档规模配额。每类型的开课配额满足容量约束(按最坏 weeklySessions:
     * 理论/多媒体课 ≤4 次/周, 实验课 ≤2 次/周):
     * <pre>
     *   NORMAL 课次≤4:  配额*4 ≤ normalRooms*25
     *   MULTIMEDIA 课次≤4: 配额*4 ≤ multiRooms*25
     *   LAB 课次≤2:    配额*2 ≤ labRooms*10(每间每天最多 2 个非重叠双大节段, 共 5 天)
     * </pre>
     * 全部余量 ≥ 50%, 保证任意 seed 下结构可行。
     */
    private static Profile profileOf(ExperimentDatasetSize size) {
        return switch (size) {
            // N:12*4=48 ≤ 3*25=75; M:2*4=8 ≤ 25; L:2*2=4 ≤ 10
            case SMALL -> new Profile(6, 8, 3, 1, 1, 12, 2, 2);
            // N:38*4=152 ≤ 200; M:9*4=36 ≤ 75; L:8*2=16 ≤ 20
            case MEDIUM -> new Profile(18, 26, 8, 3, 2, 38, 9, 8);
            // N:90*4=360 ≤ 450; M:24*4=96 ≤ 150; L:16*2=32 ≤ 40
            case LARGE -> new Profile(40, 60, 18, 6, 4, 90, 24, 16);
        };
    }

    /** 类型按确定性配额分配: 先 NORMAL 段, 再 MULTIMEDIA 段, 最后 LAB 段 */
    private static String typeOf(int offeringIndex, Profile p) {
        if (offeringIndex < p.normalOfferings) {
            return ROOM_NORMAL;
        }
        if (offeringIndex < p.normalOfferings + p.multiOfferings) {
            return ROOM_MULTIMEDIA;
        }
        return ROOM_LAB;
    }

    // ---------- 内部: 生成器 ----------

    /** 25 个时间槽: day=1..5, period=1..5, slotId = (day-1)*5+period(升序) */
    private static List<TimeSlotInfo> buildTimeSlots() {
        List<TimeSlotInfo> slots = new ArrayList<>(DAYS * PERIODS);
        for (int day = 1; day <= DAYS; day++) {
            for (int period = 1; period <= PERIODS; period++) {
                slots.add(new TimeSlotInfo((long) (day - 1) * PERIODS + period, day, period));
            }
        }
        return slots;
    }

    /** 教室池: 容量下限均高于班级人数上界(≤40), 保证任意课程对任意同类教室可用 */
    private static List<RoomInfo> buildRooms(Random rng, Profile p) {
        List<RoomInfo> rooms = new ArrayList<>();
        long id = 3000L;
        for (int i = 0; i < p.normalRooms; i++) {
            rooms.add(new RoomInfo(++id, 85 + rng.nextInt(26), ROOM_NORMAL));
        }
        for (int i = 0; i < p.multiRooms; i++) {
            rooms.add(new RoomInfo(++id, 80 + rng.nextInt(21), ROOM_MULTIMEDIA));
        }
        for (int i = 0; i < p.labRooms; i++) {
            rooms.add(new RoomInfo(++id, 75 + rng.nextInt(16), ROOM_LAB));
        }
        return rooms;
    }

    /** 理论/多媒体课每周 1..4 次(1:10%, 2:60%, 3:20%, 4:10%) */
    private static int theoryWeeklySessions(Random rng) {
        int r = rng.nextInt(10);
        if (r == 0) {
            return 1;
        }
        if (r < 7) {
            return 2;
        }
        if (r < 9) {
            return 3;
        }
        return 4;
    }

    /** 每位教师: 随机 4 个不偏好(-1) + 3 个偏好(+1) 时间槽(同一槽不重复) */
    private static Map<Long, Map<Long, Integer>> buildTeacherPreferences(Random rng, List<Long> teacherIds) {
        List<Long> allSlotIds = new ArrayList<>(DAYS * PERIODS);
        for (long slot = 1; slot <= DAYS * PERIODS; slot++) {
            allSlotIds.add(slot);
        }
        Map<Long, Map<Long, Integer>> preferences = new LinkedHashMap<>();
        for (long teacherId : teacherIds) {
            List<Long> order = new ArrayList<>(allSlotIds);
            Collections.shuffle(order, rng);
            Map<Long, Integer> levelBySlot = new LinkedHashMap<>();
            for (int i = 0; i < PREF_NEGATIVE; i++) {
                levelBySlot.put(order.get(i), -1);
            }
            for (int i = PREF_NEGATIVE; i < PREF_NEGATIVE + PREF_POSITIVE; i++) {
                levelBySlot.put(order.get(i), 1);
            }
            preferences.put(teacherId, levelBySlot);
        }
        return preferences;
    }

    private static List<Long> range(long start, int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(start + i);
        }
        return ids;
    }
}
