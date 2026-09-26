package com.scheduling.algorithm;

/**
 * 每次温度迭代后的采样点(纯 POJO, 落库到 scheduling_result.iteration_history 的 JSON 结构)。
 *
 * @param tempIteration  外循环迭代序号(1-based)
 * @param temperature    本次采样时的温度(已降温)
 * @param currentEnergy  当前解能量
 * @param bestEnergy     至今最优能量
 * @param hardViolation  当前解硬约束违反数
 * @param softPenalty    当前解软约束原始违反合计(未加权)
 */
public record IterationPoint(
        int tempIteration,
        double temperature,
        double currentEnergy,
        double bestEnergy,
        int hardViolation,
        double softPenalty) {
}
