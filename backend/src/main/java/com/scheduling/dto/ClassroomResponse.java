package com.scheduling.dto;

import com.scheduling.entity.Classroom;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教室响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomResponse {

    private Long id;
    private String roomNo;
    private String building;
    private Integer capacity;
    private String roomType;

    public static ClassroomResponse from(Classroom classroom) {
        return ClassroomResponse.builder()
                .id(classroom.getId())
                .roomNo(classroom.getRoomNo())
                .building(classroom.getBuilding())
                .capacity(classroom.getCapacity())
                .roomType(classroom.getRoomType())
                .build();
    }
}
