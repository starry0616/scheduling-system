package com.scheduling.algorithm;

import java.util.Locale;

/**
 * 单个排课解的适应度分解(能量 = 硬违反 x W_hard + 加权软惩罚, V2.2 §8/§9)。
 *
 * <p>供调试、历史采样、结果落库与实验统计使用; 各字段含义:
 * <pre>
 *   energy          = hardViolation * W_hard + w1*S1 + ... + w6*S6
 *   hardViolation   = H1 + H2 + H3 + H7(硬约束违反总数, 越小越优, 理想 0)
 *   S1..S6         = 6 个软约束原始惩罚量(未加权)
 *   softPenalty    = S1 + ... + S6(未加权原始软违反合计, 落库软违反数口径)
 * </pre>
 */
public final class FitnessBreakdown {

    private final double energy;

    // 硬约束
    private final int h1;   // 教师同时间段冲突
    private final int h2;   // 班级同时间段冲突
    private final int h3;   // 教室同时间段冲突
    private final int h7;   // 同一开课实例不同课次同一天
    private final int hardViolation;

    // 软约束(原始量, 未加权)
    private final double s1;   // 教师时间偏好(S1)
    private final double s2;   // 日期分布均衡(S2)
    private final double s3;   // 学生每天课量均衡(S3)
    private final double s4;   // 教师连续课节(S4)
    private final double s5;   // 学生空课时间(S5)
    private final double s6;   // 早晚节避免(S6)
    private final double softPenalty;

    FitnessBreakdown(double energy,
                     int h1, int h2, int h3, int h7,
                     double s1, double s2, double s3, double s4, double s5, double s6) {
        this.energy = energy;
        this.h1 = h1;
        this.h2 = h2;
        this.h3 = h3;
        this.h7 = h7;
        this.hardViolation = h1 + h2 + h3 + h7;
        this.s1 = s1;
        this.s2 = s2;
        this.s3 = s3;
        this.s4 = s4;
        this.s5 = s5;
        this.s6 = s6;
        this.softPenalty = s1 + s2 + s3 + s4 + s5 + s6;
    }

    public double getEnergy() {
        return energy;
    }

    public int getH1() {
        return h1;
    }

    public int getH2() {
        return h2;
    }

    public int getH3() {
        return h3;
    }

    public int getH7() {
        return h7;
    }

    public int getHardViolation() {
        return hardViolation;
    }

    public double getS1() {
        return s1;
    }

    public double getS2() {
        return s2;
    }

    public double getS3() {
        return s3;
    }

    public double getS4() {
        return s4;
    }

    public double getS5() {
        return s5;
    }

    public double getS6() {
        return s6;
    }

    /** 软约束原始违反合计(未加权) */
    public double getSoftPenalty() {
        return softPenalty;
    }

    public boolean isHardFeasible() {
        return hardViolation == 0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "Fitness{energy=%.2f, hard=%d(H1=%d,H2=%d,H3=%d,H7=%d), "
                        + "soft=%.2f(S1=%.2f,S2=%.2f,S3=%.2f,S4=%.2f,S5=%.2f,S6=%.2f)}",
                energy, hardViolation, h1, h2, h3, h7,
                softPenalty, s1, s2, s3, s4, s5, s6);
    }
}
