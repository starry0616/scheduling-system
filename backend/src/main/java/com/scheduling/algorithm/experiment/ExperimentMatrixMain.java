package com.scheduling.algorithm.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标准实验矩阵执行入口(阶段8.3)。
 *
 * <p>纯 JDK main(无 Spring/DB), 直接:
 * <pre>
 *   java -cp target/classes com.scheduling.algorithm.experiment.ExperimentMatrixMain [outDir] [families] [seedRange] [planFilter]
 * </pre>
 *
 * <ul>
 *   <li>outDir    : 输出目录, 默认 "experiment-output";</li>
 *   <li>families  : 逗号分隔实验族 baseline/initial-strategy/cooling-rate/neighbors-per-temp
 *                   (默认 all = 全部四个);</li>
 *   <li>seedRange : "all"(默认)/"N"(前 N 个 seed)/"A-B"(1 起算闭区间),
 *                   用于把大实验族分片执行(每片写独立输出目录, 最后合并);</li>
 *   <li>planFilter: 可选, 只执行 planId 含该子串(不区分大小写)的组,
 *                   便于只跑某规模/某策略(如 "LARGE-RANDOM")。</li>
 * </ul>
 *
 * <p>输出:
 * <ul>
 *   <li>{family}.csv            —— 该次执行涉及组的逐行矩阵 CSV(UTF-8, 字段见 MATRIX_HEADER);</li>
 *   <li>statistics-summary.txt  —— 每组描述性统计(feasibleCount/rate、mean、
 *       median/min/max/sd × bestEnergy/softPenalty/iterations/totalNeighborEvals/runtimeMs)。</li>
 * </ul>
 */
public final class ExperimentMatrixMain {

    private ExperimentMatrixMain() {
    }

    public static void main(String[] args) throws IOException {
        String outDirArg = args.length > 0 && !args[0].isBlank() ? args[0] : "experiment-output";
        String familiesArg = args.length > 1 && !args[1].isBlank() ? args[1] : "all";
        String rangeArg = args.length > 2 && !args[2].isBlank() ? args[2] : "all";
        String planFilter = args.length > 3 && !args[3].isBlank() ? args[3].trim() : "";

        Path outDir = Path.of(outDirArg).toAbsolutePath().normalize();
        Files.createDirectories(outDir);

        Map<String, List<ExperimentPlan>> all = ExperimentMatrix.families();
        List<String> selected = new ArrayList<>();
        if (familiesArg.equalsIgnoreCase("all")) {
            selected.addAll(ExperimentMatrix.FAMILY_NAMES);
        } else {
            for (String token : familiesArg.split(",")) {
                String name = token.trim();
                if (!all.containsKey(name)) {
                    throw new IllegalArgumentException("未知实验族: " + name
                            + " (可选: " + ExperimentMatrix.FAMILY_NAMES + ")");
                }
                selected.add(name);
            }
        }

        StringBuilder summary = new StringBuilder();
        long totalRuns = 0;
        for (String family : selected) {
            List<ExperimentPlan> plans = filterPlans(all.get(family), planFilter);
            plans = sliceSeeds(plans, rangeArg);
            List<ExperimentPlanResult> planResults = ExperimentPlanRunner.runAll(plans);

            Path csv = outDir.resolve(family + ".csv");
            ExperimentCsvExporter.writeMatrix(csv, planResults);

            long runs = planResults.stream().mapToLong(p -> p.results().size()).sum();
            totalRuns += runs;

            summary.append("# experiment family: ").append(family)
                    .append(" (").append(planResults.size()).append(" plans, ")
                    .append(runs).append(" runs, seeds=").append(rangeArg)
                    .append(planFilter.isEmpty() ? "" : ", filter=" + planFilter)
                    .append(")\n");
            for (ExperimentPlanResult planResult : planResults) {
                summary.append(ExperimentStatistics.groupSummaryText(planResult));
            }
        }

        summary.insert(0, "Experiment matrix summary - total runs: " + totalRuns + "\n\n");
        Files.writeString(outDir.resolve("statistics-summary.txt"), summary.toString(),
                StandardCharsets.UTF_8);

        System.out.println("output dir: " + outDir);
        System.out.println("total runs executed: " + totalRuns);
        System.out.println(summary);
    }

    private static List<ExperimentPlan> filterPlans(List<ExperimentPlan> plans, String planFilter) {
        if (planFilter.isEmpty()) {
            return plans;
        }
        List<ExperimentPlan> out = new ArrayList<>();
        for (ExperimentPlan plan : plans) {
            if (plan.planId().toLowerCase().contains(planFilter.toLowerCase())) {
                out.add(plan);
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("planFilter 无匹配组: " + planFilter);
        }
        return out;
    }

    /** seedRange: "all" | "N"(前 N 个) | "A-B"(1 起算闭区间) */
    private static List<ExperimentPlan> sliceSeeds(List<ExperimentPlan> plans, String rangeArg) {
        String range = rangeArg.trim();
        int from = 1;
        int toExclusive = Integer.MAX_VALUE; // 不限制
        if (!range.equalsIgnoreCase("all") && !range.equals("0")) {
            if (range.matches("\\d+")) {
                toExclusive = Integer.parseInt(range) + 1;
            } else if (range.matches("\\d+-\\d+")) {
                String[] bounds = range.split("-");
                from = Integer.parseInt(bounds[0]);
                toExclusive = Integer.parseInt(bounds[1]) + 1;
                if (from < 1 || toExclusive - 1 < from) {
                    throw new IllegalArgumentException("非法 seedRange: " + range
                            + " (A-B 需 1 ≤ A ≤ B)");
                }
            } else {
                throw new IllegalArgumentException("非法 seedRange: " + range
                        + " (可选: all | N | A-B)");
            }
        }

        List<ExperimentPlan> out = new ArrayList<>(plans.size());
        for (ExperimentPlan plan : plans) {
            List<Long> seeds = plan.experimentSeeds();
            int lo = Math.max(0, from - 1);
            int hi = Math.min(seeds.size(), toExclusive);
            if (lo >= hi) {
                throw new IllegalArgumentException(
                        "seedRange " + rangeArg + " 超出组 " + plan.planId()
                                + " 的 seed 范围 1.." + seeds.size());
            }
            out.add(new ExperimentPlan(plan.planId(), plan.datasetSize(),
                    plan.datasetSeed(), plan.initialStrategy(), plan.params(),
                    List.copyOf(seeds.subList(lo, hi))));
        }
        return out;
    }
}
