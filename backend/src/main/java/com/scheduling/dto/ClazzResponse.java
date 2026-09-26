package com.scheduling.dto;

import com.scheduling.entity.Clazz;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 班级响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClazzResponse {

    private Long id;
    private String className;
    private String grade;
    private Integer studentCount;
    private String department;

    public static ClazzResponse from(Clazz clazz) {
        return ClazzResponse.builder()
                .id(clazz.getId())
                .className(clazz.getClassName())
                .grade(clazz.getGrade())
                .studentCount(clazz.getStudentCount())
                .department(clazz.getDepartment())
                .build();
    }
}
