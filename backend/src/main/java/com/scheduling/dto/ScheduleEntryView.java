package com.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 排课结果单条目视图(阶段七前端接入, 只读展示用)
 *
 * 承载课表渲染所需全部字段:
 *   - 时间: 基于 (dayOfWeek, period) 起止, 连续占用 durationSlots 个大节(禁止用 slotId+1 推断);
 *   - 课程/教师/教室/班级: 冗余展示名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleEntryView {

    private Long entryId;
    private Long taskId;
    private Long offeringId;
    private Integer unitIndex;

    /** 该课次每次连续占用的大节数(来自 offering.durationSlots) */
    private Integer durationSlots;
    private Boolean isLabCourse;

    private Integer dayOfWeek;
    private Integer period;
    private String startTime;
    private String endTime;

    private Long courseId;
    private String courseCode;
    private String courseName;
    private String courseType;
    private String requiredRoomType;

    private Long teacherId;
    private String teacherNo;
    private String teacherName;

    private Long classroomId;
    private String roomNo;
    private String building;
    private String roomType;

    /** 该课次覆盖的授课班级(合班含多班, 按关联顺序) */
    private List<ClassBrief> classes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassBrief {
        private Long id;
        private String className;
        private String grade;
    }
}
