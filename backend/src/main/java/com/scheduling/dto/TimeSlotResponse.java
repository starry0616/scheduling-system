package com.scheduling.dto;

import com.scheduling.entity.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 时间段响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotResponse {

    private Long id;
    private Integer dayOfWeek;
    private Integer period;
    private String startTime;
    private String endTime;

    public static TimeSlotResponse from(TimeSlot slot) {
        return TimeSlotResponse.builder()
                .id(slot.getId())
                .dayOfWeek(slot.getDayOfWeek())
                .period(slot.getPeriod())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .build();
    }
}
