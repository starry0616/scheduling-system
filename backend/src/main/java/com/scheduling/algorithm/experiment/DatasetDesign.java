package com.scheduling.algorithm.experiment;

/**
 * 数据集规模设计值(阶段8.2-B)。
 *
 * <p>暴露每个规模的标准配置数, 供论文数据描述与测试校验使用;
 * 与 {@link DatasetFactory} 内部生成逻辑一一对应。
 *
 * @param teacherCount        教师数
 * @param classCount          班级数
 * @param classroomCount      教室池数(NORMAL + MULTIMEDIA + LAB)
 * @param courseOfferingCount 开课实例数(按 weeklySessions 展开为 SchedulingUnit)
 */
public record DatasetDesign(int teacherCount,
                            int classCount,
                            int classroomCount,
                            int courseOfferingCount) {
}
