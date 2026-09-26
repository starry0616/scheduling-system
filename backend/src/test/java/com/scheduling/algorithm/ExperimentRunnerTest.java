package com.scheduling.algorithm;

import com.scheduling.algorithm.experiment.ExperimentBatchRunner;
import com.scheduling.algorithm.experiment.ExperimentCsvExporter;
import com.scheduling.algorithm.experiment.ExperimentResult;
import com.scheduling.algorithm.experiment.ExperimentRunner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段8.2-A 实验运行器测试(全部使用极小问题 + 少量迭代, 快速完成)。
 *
 * <p>覆盖:
 * <ol>
 *   <li>同 problem+params+seed+strategy 运行两次, 关键结果完全一致(Test 1);</li>
 *   <li>RANDOM + seed1 vs seed2 产生不同随机初始状态/搜索路径(Test 2);</li>
 *   <li>GREEDY + 同 seed 两次运行可重复(Test 3);</li>
 *   <li>批量 seeds=[1..5] 得 5 条结果且 seed 一一对应、相互隔离(Test 4);</li>
 *   <li>CSV 导出 header/行数/可读回/数值格式(Test 5)。</li>
 * </ol>
 */
class ExperimentRunnerTest {

    /** 极小实例: 4 开课实例 x 每周 2 次课 = 8 unit, 2 教室 */
    private static SchedulingProblem buildProblem() {
        List<SchedulingUnit> units = new ArrayList<>();
        int idx = 0;
        for (int offering = 0; offering < 4; offering++) {
            for (int session = 0; session < 2; session++) {
                units.add(AlgorithmTestSupport.unit(idx++, 100L + offering, session,
                        1000L + offering * 100L + 1L, 100L + offering,
                        2, 1, AlgorithmTestSupport.normalRooms()));
            }
        }
        return AlgorithmTestSupport.problem(units);
    }

    /** 少量迭代参数, 保证单次运行毫秒级 */
    private static SimulatedAnnealingParams tinyParams() {
        return new SimulatedAnnealingParams(1000, 10, 0.1, 0.95, 60, 6, 2);
    }

    /** 比较两条结果的所有"确定性关键量"(排除 experimentId 与 runtimeMs) */
    private static void assertSameKeyState(ExperimentResult expected, ExperimentResult actual, String msg) {
        assertNotNull(expected, msg);
        assertNotNull(actual, msg);
        assertEquals(expected.seed(), actual.seed(), msg);
        assertEquals(expected.initialStrategy(), actual.initialStrategy(), msg);
        assertEquals(expected.initialFitness(), actual.initialFitness(), 0.0, msg);
        assertEquals(expected.initialHardViolation(), actual.initialHardViolation(), msg);
        assertEquals(expected.initialSoftPenalty(), actual.initialSoftPenalty(), 0.0, msg);
        assertEquals(expected.bestFitness(), actual.bestFitness(), 0.0, msg);
        assertEquals(expected.bestHardViolation(), actual.bestHardViolation(), msg);
        assertEquals(expected.bestSoftPenalty(), actual.bestSoftPenalty(), 0.0, msg);
        assertEquals(expected.tempIterations(), actual.tempIterations(), msg);
        assertEquals(expected.totalNeighborEvaluations(), actual.totalNeighborEvaluations(), msg);
        assertEquals(expected.initialTemperature(), actual.initialTemperature(), msg);
        assertEquals(expected.coolingRate(), actual.coolingRate(), 0.0, msg);
        assertEquals(expected.neighborsPerTemp(), actual.neighborsPerTemp(), msg);
        assertEquals(expected.maxTempIterations(), actual.maxTempIterations(), msg);
        assertEquals(expected.minTemp(), actual.minTemp(), 0.0, msg);
        assertEquals(expected.maxInitialTemp(), actual.maxInitialTemp(), 0.0, msg);
        assertEquals(expected.minInitialTemp(), actual.minInitialTemp(), 0.0, msg);
        assertEquals(expected.hardConstraintWeight(), actual.hardConstraintWeight(), 0.0, msg);
        assertArrayEquals(expected.bestAssignment(), actual.bestAssignment(), msg);
        assertEquals(expected.history(), actual.history(), msg);
        assertTrue(actual.runtimeMs() >= 0, msg + " runtimeMs 必须 >= 0");
    }

    // ---------- Test 1: 同配置两次运行完全一致 ----------

    @Test
    void 同problem参数seed策略运行两次关键结果完全一致() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = tinyParams();
        for (InitialStrategy strategy : InitialStrategy.values()) {
            ExperimentResult r1 = ExperimentRunner.run(p, params, 42L, strategy);
            ExperimentResult r2 = ExperimentRunner.run(p, params, 42L, strategy);
            assertSameKeyState(r1, r2,
                    "GREEDY/RANDOM 同 seed 两次运行必须一致: " + strategy);
            assertEquals(strategy, r1.initialStrategy());
            assertEquals(strategy, r2.initialStrategy());
        }
    }

    // ---------- Test 2: 不同 seed 产生不同随机初始状态/搜索结果 ----------

    @Test
    void random不同seed产生不同初始状态或搜索路径() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = tinyParams();
        ExperimentResult r1 = ExperimentRunner.run(p, params, 1L, InitialStrategy.RANDOM);
        ExperimentResult r2 = ExperimentRunner.run(p, params, 2L, InitialStrategy.RANDOM);

        assertEquals(InitialStrategy.RANDOM, r1.initialStrategy());
        assertEquals(InitialStrategy.RANDOM, r2.initialStrategy());
        assertEquals(1L, r1.seed());
        assertEquals(2L, r2.seed());
        assertTrue(r1.runtimeMs() >= 0);
        assertTrue(r2.runtimeMs() >= 0);

        // 不要求 bestFitness 一定不同; 断言 初始能量/硬违反/软惩罚/最优Assignment/历史 至少一处不同。
        // 8 unit x 50 候选的搜索空间下, 固定 seed 1/2 的行为确定且显著不同。
        boolean differ = r1.initialFitness() != r2.initialFitness()
                || r1.initialHardViolation() != r2.initialHardViolation()
                || r1.initialSoftPenalty() != r2.initialSoftPenalty()
                || !Arrays.equals(r1.bestAssignment(), r2.bestAssignment())
                || !r1.history().equals(r2.history());
        assertTrue(differ, "seed 1/2 的随机初始状态或搜索路径应不同");
    }

    // ---------- Test 3: GREEDY + 同 seed 两次可重复 ----------

    @Test
    void greedy同seed两次运行完全一致() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = tinyParams();
        ExperimentResult r1 = ExperimentRunner.run(p, params, 20260906L, InitialStrategy.GREEDY);
        ExperimentResult r2 = ExperimentRunner.run(p, params, 20260906L, InitialStrategy.GREEDY);
        assertSameKeyState(r1, r2, "GREEDY 同 seed 必须可重复");
        assertEquals(InitialStrategy.GREEDY, r1.initialStrategy());
    }

    // ---------- Test 4: 批量 seeds=[1..5] ----------

    @Test
    void 批量seeds1到5得到5条结果且一一对应并相互隔离() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = tinyParams();
        List<Long> seeds = List.of(1L, 2L, 3L, 4L, 5L);

        List<ExperimentResult> batch =
                ExperimentBatchRunner.run(p, params, InitialStrategy.RANDOM, seeds);

        assertEquals(5, batch.size(), "批量必须返回 5 条结果");
        for (int i = 0; i < seeds.size(); i++) {
            ExperimentResult r = batch.get(i);
            assertEquals(seeds.get(i), r.seed(), "结果与 seed 必须一一对应: index=" + i);
            assertEquals(InitialStrategy.RANDOM, r.initialStrategy());
            assertTrue(r.runtimeMs() >= 0);

            // 隔离验证: batch.get(i) 与"单独运行同一 seed"的关键量完全一致(不共享随机状态)
            ExperimentResult standalone =
                    ExperimentRunner.run(p, params, seeds.get(i), InitialStrategy.RANDOM);
            assertSameKeyState(r, standalone,
                    "批量第 " + i + " 条(seed=" + seeds.get(i) + ") 必须与单独运行一致, 不受相邻实验影响");
        }
    }

    // ---------- Test 5: CSV 导出 ----------

    @Test
    void csv导出_header存在行数正确可读回且数值无格式错误() {
        SchedulingProblem p = buildProblem();
        SimulatedAnnealingParams params = tinyParams();
        List<ExperimentResult> results = new ArrayList<>();
        // 用含逗号 / 含引号的 experimentId 验证 RFC-4180 转义后仍可读回
        results.add(ExperimentRunner.run("对照组,含逗号", p, params, 10L, InitialStrategy.GREEDY));
        results.add(ExperimentRunner.run("含\"引号\"的组", p, params, 11L, InitialStrategy.RANDOM));
        results.add(ExperimentRunner.run("normal-group", p, params, 12L, InitialStrategy.GREEDY));

        String csv = ExperimentCsvExporter.toCsv(results);
        List<String[]> rows = parseCsv(csv);

        // header 存在且列名正确
        assertEquals(results.size() + 1, rows.size(), "CSV 行数 = result 数 + 1");
        String[] header = rows.get(0);
        assertEquals(ExperimentCsvExporter.HEADER, List.of(header), "header 必须与字段定义一致");
        for (String required : List.of("experimentId", "seed", "initialStrategy",
                "initialFitness", "initialHardViolation", "initialSoftPenalty",
                "bestFitness", "bestHardViolation", "bestSoftPenalty",
                "tempIterations", "totalNeighborEvaluations", "runtimeMs",
                "coolingRate", "neighborsPerTemp", "maxTempIterations",
                "minTemp", "maxInitialTemp", "minInitialTemp",
                "hardConstraintWeight", "feasible")) {
            assertTrue(List.of(header).contains(required), "缺少必需字段: " + required);
        }

        // 列宽一致
        for (String[] row : rows) {
            assertEquals(header.length, row.length, "每行列数必须一致");
        }

        Map<String, Integer> col = new LinkedHashMap<>();
        for (int j = 0; j < header.length; j++) {
            col.put(header[j], j);
        }
        List<String> notNumeric =
                List.of("experimentId", "initialStrategy", "initialTemperature", "feasible");

        for (int i = 0; i < results.size(); i++) {
            String[] row = rows.get(i + 1);
            ExperimentResult expected = results.get(i);

            // 字符串字段可读回(含逗号/引号/自动 id)
            assertEquals(expected.experimentId(), row[col.get("experimentId")],
                    "experimentId 必须无失真读回(含转义场景)");
            assertEquals(expected.initialStrategy().name(), row[col.get("initialStrategy")]);

            // 空值处理: initialTemperature 为 null 时导出空单元格
            assertEquals("", row[col.get("initialTemperature")], "nullable 字段必须导出为空单元格");

            // 数值列可被 Double.parseDouble 读回且无 NaN; 整型列与期望一致
            for (String column : header) {
                if (notNumeric.contains(column)) {
                    continue;
                }
                String cell = row[col.get(column)];
                assertFalse(cell.isBlank(), "数值列不应为空: " + column);
                double parsed = Double.parseDouble(cell);
                assertTrue(Double.isFinite(parsed), "数值格式错误: " + column + "=" + cell);
            }
            assertEquals(expected.seed(), Long.parseLong(row[col.get("seed")]));
            assertEquals(expected.bestHardViolation(),
                    Integer.parseInt(row[col.get("bestHardViolation")]));
            assertEquals(expected.bestFitness(), Double.parseDouble(row[col.get("bestFitness")]), 0.0,
                    "bestFitness 必须无损往返");
            assertEquals(expected.feasible(), Boolean.parseBoolean(row[col.get("feasible")]));
        }
    }

    @Test
    void csv空结果仅输出header() {
        String csv = ExperimentCsvExporter.toCsv(List.of());
        List<String[]> rows = parseCsv(csv);
        assertEquals(1, rows.size(), "空结果也必须输出 header 一行");
        assertEquals(ExperimentCsvExporter.HEADER, List.of(rows.get(0)));
    }

    /** 支持 RFC-4180 双引号转义的简易 CSV 读取器(仅测试用) */
    private static List<String[]> parseCsv(String csv) {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = csv.length();
        while (i < n) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && csv.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    cell.append(c);
                    i++;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    row.add(cell.toString());
                    cell.setLength(0);
                    i++;
                } else if (c == '\n') {
                    row.add(cell.toString());
                    cell.setLength(0);
                    rows.add(row.toArray(new String[0]));
                    row = new ArrayList<>();
                    i++;
                } else if (c == '\r') {
                    i++;
                } else {
                    cell.append(c);
                    i++;
                }
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row.toArray(new String[0]));
        }
        return rows;
    }
}
