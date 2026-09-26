package com.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 排课任务"添加课程"候选开课实例 VO (阶段七前端接入新增, 只读展示用)
 *
 * 与 CourseOfferingResponse 的差异: 附带课程代码/类型以及覆盖班级集合,
 * 供管理员在配置任务课程范围时区分"同课程同教师的多个分班开课"。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOfferingOptionResponse {

    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String courseType;
    private Long teacherId;
    private String teacherName;
    private String semester;
    private Integer weeklySessions;
    private Integer durationSlots;
    private Boolean isLabCourse;

    /** 覆盖班级ID(按关联建立顺序) */
    private List<Long> classIds;

    /** 覆盖班级名称(按关联建立顺序, 用于展示/区分) */
    private List<String> classNames;
}
