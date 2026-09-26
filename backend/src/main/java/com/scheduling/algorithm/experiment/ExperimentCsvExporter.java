package com.scheduling.algorithm.experiment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 实验结果 CSV 导出器(阶段8.2-A)。
 *
 * <p>纯 JDK 实现, 不依赖第三方; RFC-4180 风格的字段转义
 * (值含 逗号/双引号/换行 时以双引号包裹并将内部双引号翻倍)。
 * 空值输出为空单元格; 数值输出与 Locale 无关、可被 Double.parseDouble 读回。
 */
public final class ExperimentCsvExporter {

    /** 首行固定字段名(顺序即列顺序) */
    public static final List<String> HEADER = List.of(
            "experimentId",
            "seed",
            "initialStrategy",
            "initialFitness",
            "initialHardViolation",
            "initialSoftPenalty",
            "bestFitness",
            "bestHardViolation",
            "bestSoftPenalty",
            "tempIterations",
            "totalNeighborEvaluations",
            "runtimeMs",
            "initialTemperature",
            "coolingRate",
            "neighborsPerTemp",
            "maxTempIterations",
            "minTemp",
            "maxInitialTemp",
            "minInitialTemp",
            "hardConstraintWeight",
            "feasible");

    private ExperimentCsvExporter() {
    }

    /** 导出为 CSV 文本: 第一行为字段名, 每行一条结果, 行数 = results.size() + 1 */
    public static String toCsv(List<ExperimentResult> results) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADER));
        for (ExperimentResult r : results) {
            lines.add(toRow(r));
        }
        return String.join("\n", lines);
    }

    /** 直接写文件(UTF-8) */
    public static void write(Path file, List<ExperimentResult> results) throws IOException {
        Files.writeString(file, toCsv(results), StandardCharsets.UTF_8);
    }

    // ---------- 阶段8.3: 矩阵 CSV(带数据集上下文, 平面逐行) ----------

    /**
     * 矩阵 CSV 列定义(在原 21 列基础上置于最前插入数据集上下文,
     * 并按其书面指标名输出: bestEnergy=bestFitness, hardViolation=bestHardViolation,
     * softPenalty=bestSoftPenalty, iterations=tempIterations,
     * totalNeighborEvals=totalNeighborEvaluations, experimentSeed=seed)。
     * 原有 {@link #toCsv(List)} / {@link #HEADER} 保持阶段8.2-A 契约不变。
     */
    public static final List<String> MATRIX_HEADER = List.of(
            "experimentId",
            "datasetSize",
            "datasetSeed",
            "experimentSeed",
            "initialStrategy",
            "coolingRate",
            "neighborsPerTemp",
            "maxTempIterations",
            "minTemp",
            "maxInitialTemp",
            "minInitialTemp",
            "initialFitness",
            "initialHardViolation",
            "initialSoftPenalty",
            "bestEnergy",
            "hardViolation",
            "softPenalty",
            "iterations",
            "totalNeighborEvals",
            "runtimeMs",
            "hardConstraintWeight",
            "feasible");

    /** 导出多组实验计划结果为矩阵 CSV: 第一行 header, 之后每组每 seed 一行, 保持组与 seed 顺序 */
    public static String toMatrixCsv(List<ExperimentPlanResult> planResults) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", MATRIX_HEADER));
        for (ExperimentPlanResult planResult : planResults) {
            ExperimentPlan plan = planResult.plan();
            for (ExperimentResult r : planResult.results()) {
                lines.add(toMatrixRow(plan, r));
            }
        }
        return String.join("\n", lines);
    }

    /** 矩阵 CSV 直接写文件(UTF-8) */
    public static void writeMatrix(Path file, List<ExperimentPlanResult> planResults) throws IOException {
        Files.writeString(file, toMatrixCsv(planResults), StandardCharsets.UTF_8);
    }

    private static String toMatrixRow(ExperimentPlan plan, ExperimentResult r) {
        List<String> cells = new ArrayList<>(MATRIX_HEADER.size());
        cells.add(escapeField(r.experimentId()));
        cells.add(escapeField(plan.datasetSize().name()));
        cells.add(Long.toString(plan.datasetSeed()));
        cells.add(Long.toString(r.seed()));
        cells.add(escapeField(r.initialStrategy().name()));
        cells.add(fmt(r.coolingRate()));
        cells.add(Integer.toString(r.neighborsPerTemp()));
        cells.add(Integer.toString(r.maxTempIterations()));
        cells.add(fmt(r.minTemp()));
        cells.add(fmt(r.maxInitialTemp()));
        cells.add(fmt(r.minInitialTemp()));
        cells.add(fmt(r.initialFitness()));
        cells.add(Integer.toString(r.initialHardViolation()));
        cells.add(fmt(r.initialSoftPenalty()));
        cells.add(fmt(r.bestFitness()));          // bestEnergy
        cells.add(Integer.toString(r.bestHardViolation())); // hardViolation
        cells.add(fmt(r.bestSoftPenalty()));      // softPenalty
        cells.add(Integer.toString(r.tempIterations()));     // iterations
        cells.add(Long.toString(r.totalNeighborEvaluations())); // totalNeighborEvals
        cells.add(Long.toString(r.runtimeMs()));
        cells.add(fmt(r.hardConstraintWeight()));
        cells.add(Boolean.toString(r.feasible()));
        return String.join(",", cells);
    }

    private static String toRow(ExperimentResult r) {
        List<String> cells = new ArrayList<>(HEADER.size());
        cells.add(escapeField(r.experimentId()));
        cells.add(Long.toString(r.seed()));
        cells.add(escapeField(r.initialStrategy().name()));
        cells.add(fmt(r.initialFitness()));
        cells.add(Integer.toString(r.initialHardViolation()));
        cells.add(fmt(r.initialSoftPenalty()));
        cells.add(fmt(r.bestFitness()));
        cells.add(Integer.toString(r.bestHardViolation()));
        cells.add(fmt(r.bestSoftPenalty()));
        cells.add(Integer.toString(r.tempIterations()));
        cells.add(Long.toString(r.totalNeighborEvaluations()));
        cells.add(Long.toString(r.runtimeMs()));
        cells.add(r.initialTemperature() == null ? "" : fmt(r.initialTemperature()));
        cells.add(fmt(r.coolingRate()));
        cells.add(Integer.toString(r.neighborsPerTemp()));
        cells.add(Integer.toString(r.maxTempIterations()));
        cells.add(fmt(r.minTemp()));
        cells.add(fmt(r.maxInitialTemp()));
        cells.add(fmt(r.minInitialTemp()));
        cells.add(fmt(r.hardConstraintWeight()));
        cells.add(Boolean.toString(r.feasible()));
        return String.join(",", cells);
    }

    /** 标准 CSV 字段转义 */
    static String escapeField(String raw) {
        if (raw == null) {
            return "";
        }
        boolean needsQuote = raw.indexOf(',') >= 0
                || raw.indexOf('"') >= 0
                || raw.indexOf('\n') >= 0
                || raw.indexOf('\r') >= 0;
        if (!needsQuote) {
            return raw;
        }
        return '"' + raw.replace("\"", "\"\"") + '"';
    }

    /**
     * 数值格式化: 整数形态直接输出整数(如 1000, 5), 其余输出 Double.toString;
     * 一律使用 '.' 小数分隔且无千分位, Locale 无关, Double.parseDouble 可读回。
     */
    private static String fmt(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? "Infinity" : "-Infinity";
        }
        if (v == Math.rint(v) && Math.abs(v) < 9.007199254740992E15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
