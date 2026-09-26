package com.scheduling.algorithm.experiment;

/**
 * 标准实验数据集规模(阶段8.2-B, V2.2 §24.3)。
 *
 * <p>SMALL  : 单元测试式实验 / 算法流程验证 / 参数快速实验;
 * MEDIUM  : 主要参数敏感性实验(GREEDY vs RANDOM、coolingRate、neighborsPerTemp、maxTempIterations);
 * LARGE   : 算法扩展性 / runtime / 搜索空间规模 / 最终性能实验。
 *
 * <p>三档规模结构(教师 / 班级 / 教室 / 开课实例)均由 DatasetFactory 按真实业务语义生成,
 * 满足 SMALL &lt; MEDIUM &lt; LARGE。
 */
public enum ExperimentDatasetSize {
    SMALL,
    MEDIUM,
    LARGE
}
