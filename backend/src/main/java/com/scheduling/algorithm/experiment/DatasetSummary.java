package com.scheduling.algorithm.experiment;

/**
 * 数据集摘要(阶段8.2-B, 用于论文实验数据描述)。
 *
 * @param datasetSize            数据集规模(SMALL/MEDIUM/LARGE)
 * @param seed                   数据集生成随机种子
 * @param teacherCount           教师数(problem.distinctTeacherCount)
 * @param classCount             班级数(problem.distinctClassCount)
 * @param courseOfferingCount    开课实例数(由 unit 的 offeringId 去重)
 * @param schedulingUnitCount    排课单元数(offering 按 weeklySessions 展开)
 * @param classroomCount         教室池总数
 * @param timeslotCount          时间段数(25: 5 天 x 5 大节)
 * @param totalCandidateCount    全部 unit 候选总数
 * @param averageCandidateCount  平均每 unit 候选数(total / unitCount)
 * @param minCandidateCount      最小单 unit 候选数(> 0 保证)
 * @param maxCandidateCount      最大单 unit 候选数
 */
public record DatasetSummary(
        String datasetSize,
        long seed,
        int teacherCount,
        int classCount,
        int courseOfferingCount,
        int schedulingUnitCount,
        int classroomCount,
        int timeslotCount,
        long totalCandidateCount,
        double averageCandidateCount,
        int minCandidateCount,
        int maxCandidateCount) {
}
