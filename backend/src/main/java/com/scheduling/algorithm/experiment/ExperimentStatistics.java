package com.scheduling.algorithm.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * 实验组统计汇总(阶段8.3, 纯 JDK 描述性统计, 不做推断检验)。
 *
 * <p>同一实验组(一个 ExperimentPlanResult, 即同一 datasetSize/datasetSeed/
 * strategy/params 下多个 experiment seed)上计算: count / feasibleRate /
 * mean / median / min / max / standard deviation。
 *
 * <p>约定:
 * <ul>
 *   <li>standard deviation 采用<b>样本标准差</b>(分母 n-1); count == 1 时按 0 处理;</li>
 *   <li>feasible 判定以 bestHardViolation == 0 为准(硬违反 &gt; 0 的解不作为可行结果统计);</li>
 *   <li>仅描述实验现象, 不输出任何"显著优于"等推断性结论。</li>
 * </ul>
 */
public final class ExperimentStatistics {

    private ExperimentStatistics() {
    }

    /** 一组描述性统计量 */
    public record DescriptiveStats(
            int count,
            double mean,
            double median,
            double min,
            double max,
            double standardDeviation) {
    }

    /** 可行性汇总 */
    public record Feasibility(int count, int feasibleCount, double feasibleRate) {
    }

    // ---------- 通用计算 ----------

    /** 对给定样本数组计算描述性统计(输入必须非空; 顺序无关, 内部复制后排序) */
    public static DescriptiveStats describe(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("describe 的样本不能为空");
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);

        int n = sorted.length;
        double min = sorted[0];
        double max = sorted[n - 1];
        double sum = 0;
        for (double v : sorted) {
            sum += v;
        }
        double mean = sum / n;
        double median;
        if (n % 2 == 1) {
            median = sorted[n / 2];
        } else {
            median = (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        }

        double ss = 0;
        for (double v : sorted) {
            double d = v - mean;
            ss += d * d;
        }
        double sd = (n < 2) ? 0.0 : Math.sqrt(ss / (n - 1));
        return new DescriptiveStats(n, mean, median, min, max, sd);
    }

    private static DescriptiveStats of(List<ExperimentResult> results,
                                       ToDoubleFunction<ExperimentResult> metric) {
        List<ExperimentResult> list = List.copyOf(results);
        double[] values = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            values[i] = metric.applyAsDouble(list.get(i));
        }
        return describe(values);
    }

    // ---------- 各重点指标(与 ExperimentResult 字段一一对应) ----------

    /** 最优能量 bestEnergy(对应 result.bestFitness) */
    public static DescriptiveStats bestEnergy(List<ExperimentResult> results) {
        return of(results, ExperimentResult::bestFitness);
    }

    /** 最优解软惩罚合计 softPenalty(对应 result.bestSoftPenalty) */
    public static DescriptiveStats softPenalty(List<ExperimentResult> results) {
        return of(results, ExperimentResult::bestSoftPenalty);
    }

    /** 温度迭代次数 iterations(对应 result.tempIterations) */
    public static DescriptiveStats iterations(List<ExperimentResult> results) {
        return of(results, r -> r.tempIterations());
    }

    /** 邻域评价总数 totalNeighborEvals(对应 result.totalNeighborEvaluations) */
    public static DescriptiveStats neighborEvaluations(List<ExperimentResult> results) {
        return of(results, r -> (double) r.totalNeighborEvaluations());
    }

    /** 运行耗时 runtimeMs(性能指标, 不参与可复现性比较) */
    public static DescriptiveStats runtimeMs(List<ExperimentResult> results) {
        return of(results, r -> (double) r.runtimeMs());
    }

    /** 可行性: 硬违反 == 0 的数量与占比 */
    public static Feasibility feasibility(List<ExperimentResult> results) {
        List<ExperimentResult> list = List.copyOf(results);
        int feasible = 0;
        for (ExperimentResult r : list) {
            if (r.bestHardViolation() == 0) {
                feasible++;
            }
        }
        return new Feasibility(list.size(), feasible,
                list.isEmpty() ? 0.0 : (double) feasible / list.size());
    }

    // ---------- 文本呈现(供执行入口打印/落文件, 非 CSV) ----------

    /**
     * 一个实验组的完整统计文本(每组一段, 与单因素实验组粒度一致)。
     * 数值以 Locale.ROOT 格式化, 不因运行环境地区而异。
     */
    public static String groupSummaryText(ExperimentPlanResult planResult) {
        ExperimentPlan plan = planResult.plan();
        List<ExperimentResult> results = planResult.results();
        StringBuilder sb = new StringBuilder();

        sb.append("=== ").append(plan.planId())
                .append(" | dataset=").append(plan.datasetSize().name())
                .append(" | datasetSeed=").append(plan.datasetSeed())
                .append(" | strategy=").append(plan.initialStrategy().name())
                .append(" | coolingRate=").append(fmt(plan.params().coolingRate()))
                .append(" | neighborsPerTemp=").append(plan.params().neighborsPerTemp())
                .append(" | seeds=").append(results.size())
                .append('\n');

        Feasibility f = feasibility(results);
        sb.append("  feasibleCount/rate = ")
                .append(f.feasibleCount()).append('/').append(f.count())
                .append(" (").append(fmt(f.feasibleRate() * 100.0)).append("%)\n");

        appendMetric(sb, "bestEnergy", bestEnergy(results));
        appendMetric(sb, "softPenalty", softPenalty(results));
        appendMetric(sb, "iterations", iterations(results));
        appendMetric(sb, "totalNeighborEvals", neighborEvaluations(results));
        appendMetric(sb, "runtimeMs", runtimeMs(results));
        return sb.toString();
    }

    private static void appendMetric(StringBuilder sb, String label, DescriptiveStats s) {
        sb.append("  ").append(label)
                .append(": mean=").append(fmt(s.mean()))
                .append(" median=").append(fmt(s.median()))
                .append(" min=").append(fmt(s.min()))
                .append(" max=").append(fmt(s.max()))
                .append(" sd=").append(fmt(s.standardDeviation()))
                .append(" (n=").append(s.count()).append(")\n");
    }

    /** Locale 无关数值文本(最多 6 位小数, 用于摘要文本而非 CSV 原文) */
    public static String fmt(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? "Infinity" : "-Infinity";
        }
        return String.format(Locale.ROOT, "%.6f", v);
    }
}
