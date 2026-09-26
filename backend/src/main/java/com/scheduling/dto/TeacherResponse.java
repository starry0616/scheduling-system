package com.scheduling.dto;

import com.scheduling.entity.Teacher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教师响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherResponse {

    private Long id;
    private Long userId;
    private String teacherNo;
    private String name;
    private String title;
    private String department;

    public static TeacherResponse from(Teacher teacher) {
        return TeacherResponse.builder()
                .id(teacher.getId())
                .userId(teacher.getUserId())
                .teacherNo(teacher.getTeacherNo())
                .name(teacher.getName())
                .title(teacher.getTitle())
                .department(teacher.getDepartment())
                .build();
    }
}
