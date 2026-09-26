package com.scheduling.dto;

import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.Teacher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开课实例响应 VO
 *
 * 除表字段外附带 courseName/teacherName 冗余展示名(仅读展示, 非表列)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseOfferingResponse {

    private Long id;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private String semester;
    private Integer weeklySessions;
    private Integer durationSlots;
    private Boolean isLabCourse;

    public static CourseOfferingResponse from(CourseOffering offering, Course course, Teacher teacher) {
        return CourseOfferingResponse.builder()
                .id(offering.getId())
                .courseId(offering.getCourseId())
                .courseName(course == null ? null : course.getCourseName())
                .teacherId(offering.getTeacherId())
                .teacherName(teacher == null ? null : teacher.getName())
                .semester(offering.getSemester())
                .weeklySessions(offering.getWeeklySessions())
                .durationSlots(offering.getDurationSlots())
                .isLabCourse(offering.getIsLabCourse() != null && offering.getIsLabCourse() == 1)
                .build();
    }
}
