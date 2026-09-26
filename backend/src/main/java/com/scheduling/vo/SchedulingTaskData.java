package com.scheduling.vo;

import com.scheduling.entity.Classroom;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 排课任务完整数据装配视图 (第3步第三阶段 3C-4)
 *
 * 语义: 后续 SchedulingProblem 组装所需的全部原始数据, 从单个 SchedulingTask 一次装配取得:
 *   - task             : 任务本体(基本参数 + 算法参数 + 软约束权重 + hardConstraintWeight);
 *   - offerings        : 任务纳入的 CourseOffering(按纳入顺序) + 每个 offering 的 course/teacher/classes;
 *   - classrooms       : 任务允许使用的教室池(按纳入顺序);
 *   - timeSlots        : 全部时间段((dayOfWeek, period) 升序)。
 *
 * 本类是"Service 层稳定装配入口"的返回载体, 属于业务层数据视图,
 * 不承载任何算法结构(SchedulingProblem/SchedulingUnit 等后续阶段另行建模)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingTaskData {

    private SchedulingTask task;

    /** 任务纳入的开课实例(含每个 offering 的课程/教师/班级上下文), 按纳入顺序 */
    private List<CourseOfferingData> offerings;

    /** 任务允许使用的教室池(按纳入顺序, 空 = 尚未配置教室范围) */
    private List<Classroom> classrooms;

    /** 系统全量时间段, 按 (dayOfWeek, period) 升序(与课表/算法时间轴一致) */
    private List<TimeSlot> timeSlots;
}
