package com.scheduling.dto;

import com.scheduling.entity.Course;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {

    private Long id;
    private String courseCode;
    private String courseName;
    private String courseType;
    private String requiredRoomType;

    public static CourseResponse from(Course course) {
        return CourseResponse.builder()
                .id(course.getId())
                .courseCode(course.getCourseCode())
                .courseName(course.getCourseName())
                .courseType(course.getCourseType())
                .requiredRoomType(course.getRequiredRoomType())
                .build();
    }
}
