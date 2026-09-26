package com.scheduling.algorithm;

/**
 * 模拟退火参数(纯 POJO, V2.2 §17)。
 *
 * <p>与 scheduling_task 表存的 SA 算法参数一一对应:
 * <pre>
 *   maxInitialTemp      初始温度上限(默认 1000)
 *   minInitialTemp      初始温度下限(默认 10; 自适应 T0 = clamp(E0*1.5, min, max) 的保底)
 *   minTemp             终止温度(默认 0.1; T 降到该值以下停止外循环)
 *   coolingRate         降温系数(默认 0.95; 每外循环迭代 T *= coolingRate)
 *   maxTempIterations   温度迭代次数上限(外循环, 默认 5000)
 *   neighborsPerTemp    每温度邻域评价次数(内循环, 默认 20)
 *   maxRepairAttempts   冲突修复最大轮数(默认 3)
 * </pre>
 *
 * <p>参数"合理性"不预设, 通过实验 B/C/D 的数据确定(详见技术方案实验设计)。
 *
 * @param maxInitialTemp     初始温度上限, 必须 &gt; 0
 * @param minInitialTemp     初始温度下限, 必须 &gt; 0 且 &lt;= maxInitialTemp
 * @param minTemp            终止温度, 必须 &gt; 0
 * @param coolingRate        降温系数, 必须满足 0 &lt; coolingRate &lt; 1
 * @param maxTempIterations  外循环次数上限, 必须 &gt; 0
 * @param neighborsPerTemp   内循环次数, 必须 &gt;= 0(0 = 不做邻域搜索)
 * @param maxRepairAttempts  冲突修复轮数, 必须 &gt;= 0(0 = 不修复)
 */
public record SimulatedAnnealingParams(
        double maxInitialTemp,
        double minInitialTemp,
        double minTemp,
        double coolingRate,
        int maxTempIterations,
        int neighborsPerTemp,
        int maxRepairAttempts) {

    public SimulatedAnnealingParams {
        if (maxInitialTemp <= 0) {
            throw new IllegalArgumentException("maxInitialTemp 必须 > 0: " + maxInitialTemp);
        }
        if (minInitialTemp <= 0 || minInitialTemp > maxInitialTemp) {
            throw new IllegalArgumentException(
                    "minInitialTemp 必须满足 0 < minInitialTemp <= maxInitialTemp: "
                            + minInitialTemp + " vs " + maxInitialTemp);
        }
        if (minTemp <= 0) {
            throw new IllegalArgumentException("minTemp 必须 > 0: " + minTemp);
        }
        if (coolingRate <= 0 || coolingRate >= 1) {
            throw new IllegalArgumentException("coolingRate 必须满足 0 < coolingRate < 1: " + coolingRate);
        }
        if (maxTempIterations <= 0) {
            throw new IllegalArgumentException("maxTempIterations 必须 > 0: " + maxTempIterations);
        }
        if (neighborsPerTemp < 0) {
            throw new IllegalArgumentException("neighborsPerTemp 不能为负: " + neighborsPerTemp);
        }
        if (maxRepairAttempts < 0) {
            throw new IllegalArgumentException("maxRepairAttempts 不能为负: " + maxRepairAttempts);
        }
    }
}
