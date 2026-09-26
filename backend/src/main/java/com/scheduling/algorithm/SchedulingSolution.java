package com.scheduling.algorithm;

import java.util.Arrays;

/**
 * 算法解(纯 POJO, V2.2 §6)。
 *
 * <p>一个 SchedulingSolution = 每个 SchedulingUnit 选中其某个候选的解:
 * <pre>
 *   choices[i] = problem.units.get(i).candidates 中的下标
 * </pre>
 * 即 "每个课次(Unit)安排在哪个(教室, 起始时间段)"。候选在组装阶段已排除
 * 不可用时间/不合格教室/容量/类型问题, 因此解中的每个 Assignment 天然合法。
 */
public final class SchedulingSolution {

    /** 每个 unit 选中的候选下标; 下标 -1 表示未安排(非法, 正常流程不会出现) */
    private final int[] choices;

    public SchedulingSolution(int[] choices) {
        if (choices == null) {
            throw new IllegalArgumentException("choices 不能为 null");
        }
        this.choices = choices.clone();
    }

    public int size() {
        return choices.length;
    }

    public int getChoice(int unitIndex) {
        return choices[unitIndex];
    }

    /** 返回快照(对外安全拷贝) */
    public int[] toArray() {
        return choices.clone();
    }

    @Override
    public String toString() {
        return "SchedulingSolution{units=" + choices.length + ", choices=" + Arrays.toString(choices) + "}";
    }
}
