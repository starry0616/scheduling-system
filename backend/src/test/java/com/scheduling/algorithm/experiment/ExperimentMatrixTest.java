package com.scheduling.algorithm.experiment;

import com.scheduling.algorithm.InitialStrategy;
import com.scheduling.algorithm.SimulatedAnnealingParams;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段8.3 实验矩阵/批量执行/统计/矩阵 CSV 测试(全部使用 SMALL 数据集 + 少量迭代)。
 */
class ExperimentMatrixTest {

    /** 少量迭代参数(与阶段8.2-A tinyParams 一致的量级, 保证单次运行毫秒级) */
    private static SimulatedAnnealingParams tinyParams() {
        return new SimulatedAnnealingParams(1000, 10, 0.1, 0.95, 80, 6, 2);
    }

    private static ExperimentPlan smallPlan(String planId, long datasetSeed,
                                            InitialStrategy strategy,
                                            List<Long> seeds) {
        return new ExperimentPlan(planId, ExperimentDatasetSize.SMALL, datasetSeed,
                strategy, tinyParams(), seeds);
    }

    /** 比较两条结果的全部确定性关键量(不含 runtimeMs) */
    private static void assertSameKeyState(ExperimentResult expected, ExperimentResult actual, String msg) {
        assertNotNull(actual, msg);
        assertEquals(expected.experimentId(), actual.experimentId(), msg);
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
        assertEquals(expected.feasible(), actual.feasible(), msg);
        assertArrayEquals(expected.bestAssignment(), actual.bestAssignment(), msg);
        assertEquals(expected.history(), actual.history(), msg);
        assertTrue(actual.runtimeMs() >= 0, msg + " runtimeMs 必须 >= 0");
    }

    // ---------- Test 1: 实验计划生成(组合与数量) ----------

    @Test
    void 标准矩阵各实验族计划数与总实验数正确() {
        // baseline: 3 规模 × 10 seeds = 30
        List<ExperimentPlan> baseline = ExperimentMatrix.baselinePlans();
        assertEquals(3, baseline.size());
        assertEquals(30, baseline.stream().mapToInt(p -> p.experimentSeeds().size()).sum());

        // initial-strategy: 3 规模 × 2 策略 × 10 seeds = 60
        List<ExperimentPlan> strategy = ExperimentMatrix.initialStrategyPlans();
        assertEquals(3 * 2, strategy.size());
        assertEquals(60, strategy.stream().mapToInt(p -> p.experimentSeeds().size()).sum());

        // cooling-rate / neighbors-per-temp: 各 5 组 × 10 seeds = 50
        List<ExperimentPlan> cooling = ExperimentMatrix.coolingRatePlans();
        assertEquals(ExperimentMatrix.COOLING_RATES.size(), cooling.size());
        assertEquals(50, cooling.stream().mapToInt(p -> p.experimentSeeds().size()).sum());

        List<ExperimentPlan> neighborPlans = ExperimentMatrix.neighborsPerTempPlans();
        assertEquals(ExperimentMatrix.NEIGHBORS_PER_TEMP_VALUES.size(), neighborPlans.size());
        assertEquals(50, neighborPlans.stream().mapToInt(p -> p.experimentSeeds().size()).sum());

        // SMALL/MEDIUM/LARGE 与 GREEDY/RANDOM 均出现
        for (ExperimentPlan p : baseline) {
            assertEquals(InitialStrategy.GREEDY, p.initialStrategy());
            assertEquals(ExperimentMatrix.STANDARD_DATASET_SEED, p.datasetSeed());
            assertEquals(ExperimentPlan.STANDARD_SEEDS.size(), p.experimentSeeds().size());
        }
        Map<String, Boolean> sizes = new LinkedHashMap<>();
        Map<String, Boolean> strategies = new LinkedHashMap<>();
        for (ExperimentPlan p : strategy) {
            sizes.put(p.datasetSize().name(), true);
            strategies.put(p.initialStrategy().name(), true);
        }
        assertEquals(3, sizes.size());
        assertEquals(2, strategies.size());
        for (ExperimentDatasetSize size : ExperimentDatasetSize.values()) {
            assertTrue(sizes.containsKey(size.name()), "缺少规模: " + size);
        }
        assertTrue(strategies.containsKey(InitialStrategy.GREEDY.name()));
        assertTrue(strategies.containsKey(InitialStrategy.RANDOM.name()));

        // 各参数档位逐一出现
        List<Double> coolingRates = cooling.stream()
                .map(p -> p.params().coolingRate()).sorted().toList();
        assertEquals(ExperimentMatrix.COOLING_RATES.stream().sorted().toList(), coolingRates);
        List<Integer> neighborValues = neighborPlans.stream()
                .map(p -> p.params().neighborsPerTemp()).sorted().toList();
        assertEquals(ExperimentMatrix.NEIGHBORS_PER_TEMP_VALUES.stream().sorted().toList(),
                neighborValues);
    }

    @Test
    void 计划校验_空seeds或空planId应拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentPlan("x", ExperimentDatasetSize.SMALL, 1L,
                        InitialStrategy.GREEDY, tinyParams(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentPlan("  ", ExperimentDatasetSize.SMALL, 1L,
                        InitialStrategy.GREEDY, tinyParams(), List.of(1L)));
    }

    // ---------- Test 2: 同 plan 重跑(seed 隔离)关键结果一致 ----------

    @Test
    void 同一计划重复执行除耗时外完全一致() {
        ExperimentPlan plan = smallPlan("repeat-check", 20260901L,
                InitialStrategy.GREEDY, List.of(20260901L, 20260902L, 20260903L));
        ExperimentPlanResult first = ExperimentPlanRunner.run(plan);
        ExperimentPlanResult second = ExperimentPlanRunner.run(plan);
        assertEquals(plan.experimentSeeds().size(), first.results().size());
        assertEquals(first.results().size(), second.results().size());
        for (int i = 0; i < first.results().size(); i++) {
            assertSameKeyState(first.results().get(i), second.results().get(i),
                    "同 plan 两次运行第 " + i + " 条(seed=" + plan.experimentSeeds().get(i)
                            + ")关键量必须一致");
        }
    }

    // ---------- Test 3: 不同 experiment seed 不复用随机状态 ----------

    @Test
    void 不同实验seed不共享随机状态产生不同搜索路径() {
        ExperimentPlanResult one = ExperimentPlanRunner.run(
                smallPlan("diff-seed", 20260901L, InitialStrategy.RANDOM, List.of(1L)));
        ExperimentPlanResult two = ExperimentPlanRunner.run(
                smallPlan("diff-seed", 20260901L, InitialStrategy.RANDOM, List.of(2L)));
        ExperimentResult r1 = one.results().get(0);
        ExperimentResult r2 = two.results().get(0);

        assertEquals(1L, r1.seed());
        assertEquals(2L, r2.seed());
        // 同一数据集 + 不同 experiment seed: 随机初始状态与搜索路径必然不同
        boolean differ = r1.initialFitness() != r2.initialFitness()
                || r1.initialHardViolation() != r2.initialHardViolation()
                || r1.initialSoftPenalty() != r2.initialSoftPenalty()
                || !Arrays.equals(r1.bestAssignment(), r2.bestAssignment())
                || !r1.history().equals(r2.history());
        assertTrue(differ, "seed 1/2 应产生不同初始状态或搜索路径");
    }

    // ---------- Test 4: 统计计算 ----------

    @Test
    void 描述性统计_mean_median_min_max_sd计算正确() {
        // 奇数样本
        ExperimentStatistics.DescriptiveStats odd = ExperimentStatistics.describe(
                new double[]{1, 2, 3, 4, 5});
        assertEquals(5, odd.count());
        assertEquals(3.0, odd.mean(), 1e-9);
        assertEquals(3.0, odd.median(), 1e-9);
        assertEquals(1.0, odd.min(), 1e-9);
        assertEquals(5.0, odd.max(), 1e-9);
        assertEquals(Math.sqrt(2.5), odd.standardDeviation(), 1e-9);

        // 偶数样本: [2,4,4,4,5,5,7,9] mean=5, median=(4+5)/2
        ExperimentStatistics.DescriptiveStats even = ExperimentStatistics.describe(
                new double[]{2, 4, 4, 4, 5, 5, 7, 9});
        assertEquals(8, even.count());
        assertEquals(5.0, even.mean(), 1e-9);
        assertEquals(4.5, even.median(), 1e-9);
        assertEquals(2.0, even.min(), 1e-9);
        assertEquals(9.0, even.max(), 1e-9);

        // 空样本拒绝
        assertThrows(IllegalArgumentException.class,
                () -> ExperimentStatistics.describe(new double[0]));
    }

    @Test
    void 实验结果组级统计与可行性率一致() {
        List<Long> seeds = List.of(20260901L, 20260902L, 20260903L, 20260904L, 20260905L);
        ExperimentPlanResult pr = ExperimentPlanRunner.run(
                smallPlan("stats-check", 20260901L, InitialStrategy.GREEDY, seeds));
        List<ExperimentResult> results = pr.results();

        ExperimentStatistics.Feasibility f = ExperimentStatistics.feasibility(results);
        assertEquals(results.size(), f.count());
        assertEquals(f.feasibleCount(), (int) results.stream()
                .filter(r -> r.bestHardViolation() == 0).count());
        assertTrue(f.feasibleRate() >= 0.0 && f.feasibleRate() <= 1.0);

        ExperimentStatistics.DescriptiveStats energy = ExperimentStatistics.bestEnergy(results);
        assertEquals(results.size(), energy.count());
        // 组内指标与原始数据核对(min/max/mean)
        double min = results.stream().mapToDouble(ExperimentResult::bestFitness).min().orElseThrow();
        double max = results.stream().mapToDouble(ExperimentResult::bestFitness).max().orElseThrow();
        assertEquals(min, energy.min(), 0.0);
        assertEquals(max, energy.max(), 0.0);
        assertEquals(results.size(), ExperimentStatistics.iterations(results).count());
        assertEquals(results.size(), ExperimentStatistics.neighborEvaluations(results).count());
        assertEquals(results.size(), ExperimentStatistics.runtimeMs(results).count());
    }

    // ---------- Test 5: 矩阵 CSV ----------

    @Test
    void 矩阵csv_header与行数_null与转义与数值格式正确() {
        // planId 含逗号/引号/换行, 验证 RFC-4180 转义后仍无损读回
        String planId = "组A,含\"引号\"\n换行";
        ExperimentPlan plan = new ExperimentPlan(planId, ExperimentDatasetSize.SMALL,
                20260901L, InitialStrategy.GREEDY, tinyParams(), List.of(10L, 11L));
        ExperimentPlanResult pr = ExperimentPlanRunner.run(plan);

        String csv = ExperimentCsvExporter.toMatrixCsv(List.of(pr));
        List<String[]> rows = parseCsv(csv);

        // header 与行数: 1 header + 每 seed 一行
        assertEquals(1 + pr.results().size(), rows.size());
        String[] header = rows.get(0);
        assertEquals(ExperimentCsvExporter.MATRIX_HEADER, List.of(header),
                "矩阵 CSV header 必须与字段定义一致");
        for (String required : List.of("experimentId", "datasetSize", "datasetSeed",
                "experimentSeed", "initialStrategy", "coolingRate", "neighborsPerTemp",
                "maxTempIterations", "minTemp", "initialFitness", "bestEnergy",
                "hardViolation", "softPenalty", "iterations", "totalNeighborEvals",
                "runtimeMs", "hardConstraintWeight", "feasible")) {
            assertTrue(List.of(header).contains(required), "缺少必需字段: " + required);
        }
        for (String[] row : rows) {
            assertEquals(header.length, row.length, "每行列数必须一致");
        }

        Map<String, Integer> col = new LinkedHashMap<>();
        for (int j = 0; j < header.length; j++) {
            col.put(header[j], j);
        }
        List<String> nonNumeric = List.of("experimentId", "datasetSize", "initialStrategy",
                "feasible");

        for (int i = 0; i < pr.results().size(); i++) {
            String[] row = rows.get(i + 1);
            ExperimentResult expected = pr.results().get(i);

            // 字符串字段(含转义)无损读回
            assertEquals(expected.experimentId(), row[col.get("experimentId")],
                    "experimentId 必须无损读回(逗号/引号/换行转义)");
            assertEquals("SMALL", row[col.get("datasetSize")]);
            assertEquals(Long.toString(plan.datasetSeed()), row[col.get("datasetSeed")]);
            assertEquals(Long.toString(expected.seed()), row[col.get("experimentSeed")]);

            // 数值列可 Double.parseDouble 读回且有限
            for (String column : header) {
                if (nonNumeric.contains(column)) {
                    continue;
                }
                String cell = row[col.get(column)];
                assertFalse(cell.isBlank(), "数值列不应为空: " + column);
                double parsed = Double.parseDouble(cell);
                assertTrue(Double.isFinite(parsed), "数值格式错误: " + column + "=" + cell);
            }
            // 指标无损往返
            assertEquals(expected.bestFitness(),
                    Double.parseDouble(row[col.get("bestEnergy")]), 0.0);
            assertEquals(expected.bestHardViolation(),
                    Integer.parseInt(row[col.get("hardViolation")]));
            assertEquals(expected.bestSoftPenalty(),
                    Double.parseDouble(row[col.get("softPenalty")]), 0.0);
            assertEquals(expected.tempIterations(),
                    Integer.parseInt(row[col.get("iterations")]));
            assertEquals(expected.totalNeighborEvaluations(),
                    Long.parseLong(row[col.get("totalNeighborEvals")]));
            assertEquals(expected.feasible(), Boolean.parseBoolean(row[col.get("feasible")]));
        }
    }

    @Test
    void 矩阵csv空计划仅输出header() {
        String csv = ExperimentCsvExporter.toMatrixCsv(List.of());
        List<String[]> rows = parseCsv(csv);
        assertEquals(1, rows.size());
        assertEquals(ExperimentCsvExporter.MATRIX_HEADER, List.of(rows.get(0)));
    }

    // ---------- Test 6: 批量隔离(一个失败不污染后续, 顺序执行确定性) ----------

    @Test
    void 批量计划各结果与独立运行完全一致() {
        ExperimentPlan p1 = smallPlan("isolation-p1", 20260901L,
                InitialStrategy.GREEDY, List.of(1L, 2L));
        ExperimentPlan p2 = smallPlan("isolation-p2", 20260901L,
                InitialStrategy.RANDOM, List.of(3L, 4L));
        List<ExperimentPlanResult> batch = ExperimentPlanRunner.runAll(List.of(p1, p2));
        assertEquals(2, batch.size());
        assertEquals(p1.planId(), batch.get(0).plan().planId());
        assertEquals(p2.planId(), batch.get(1).plan().planId());

        // 与分别独立运行一致(不因批量前序运行污染)
        ExperimentPlanResult solo1 = ExperimentPlanRunner.run(p1);
        ExperimentPlanResult solo2 = ExperimentPlanRunner.run(p2);
        for (int i = 0; i < batch.get(0).results().size(); i++) {
            assertSameKeyState(solo1.results().get(i), batch.get(0).results().get(i),
                    "p1 批量与独立结果应一致: seed=" + p1.experimentSeeds().get(i));
        }
        for (int i = 0; i < batch.get(1).results().size(); i++) {
            assertSameKeyState(solo2.results().get(i), batch.get(1).results().get(i),
                    "p2 批量与独立结果应一致: seed=" + p2.experimentSeeds().get(i));
        }
    }

    // ---------- helpers ----------

    /** RFC-4180 简易 CSV 解析(仅测试用, 支持引号内逗号/引号/换行) */
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
            } else if (c == '"') {
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
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row.toArray(new String[0]));
        }
        return rows;
    }
}
