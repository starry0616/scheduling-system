package com.scheduling.vo;

import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.Teacher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 排课任务范围内的开课实例装配数据 (第3步第三阶段 3C-4)
 *
 * 语义: 从 SchedulingTask 角度装配出的"一个开课实例 + 其完整上下文",
 * 供后续 SchedulingProblem 组装读取, 避免算法层直接访问数据库:
 *   - offering   : 开课实例(含 courseId/teacherId/weeklySessions/durationSlots/isLabCourse);
 *   - course     : 冗余带出课程元数据(如 requiredRoomType 用于教室候选预过滤), 异常缺失时为 null;
 *   - teacher    : 冗余带出教师信息, 异常缺失时为 null;
 *   - classes    : 该 offering 覆盖的班级(合班), 按关联建立顺序排列;
 *                  SchedulingUnit 组装时用 classes 汇总 totalStudents(各班级人数求和)。
 *
 * 本类为纯数据视图, 不属于算法模型(SchedulingUnit/CandidateAssignment 等在后续阶段实现)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOfferingData {

    private CourseOffering offering;

    /** 课程元数据(可能为 null = offering 引用损坏, 组装阶段据此报友好错误) */
    private Course course;

    /** 教师信息(可能为 null = offering 引用损坏, 组装阶段据此报友好错误) */
    private Teacher teacher;

    /** 覆盖班级(按关联 id 升序, 稳定) */
    private List<Clazz> classes;
}
