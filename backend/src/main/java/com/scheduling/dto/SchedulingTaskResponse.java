package com.scheduling.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scheduling.entity.SchedulingTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 排课任务响应 VO (第3步第三阶段 3C-3)
 *
 * 与 scheduling_task 表列一一对应(含系统字段), 供任务管理/后续排课执行阶段展示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingTaskResponse {

    private Long id;
    private String taskName;
    private String semester;
    private Integer weekCount;
    private String status;

    // SA 算法参数
    private Double maxInitialTemp;
    private Double minInitialTemp;
    private Double minTemp;
    private Double coolingRate;
    private Integer maxTempIterations;
    private Integer neighborsPerTemp;
    private Integer maxRepairAttempts;
    private Long randomSeed;

    // 权重
    private Long hardConstraintWeight;
    // 注意: getter 为 getWXxx, 按 Bean/Jackson 规则会被 mangle 成大写 W 开头的属性名,
    //       统一显式 @JsonProperty 固定为小写驼峰命名。
    @JsonProperty("wTeacherPreference")
    private Integer wTeacherPreference;
    @JsonProperty("wCourseDistribution")
    private Integer wCourseDistribution;
    @JsonProperty("wStudentBalance")
    private Integer wStudentBalance;
    @JsonProperty("wTeacherContinuous")
    private Integer wTeacherContinuous;
    @JsonProperty("wStudentIdle")
    private Integer wStudentIdle;
    @JsonProperty("wMorningEvening")
    private Integer wMorningEvening;

    private LocalDateTime createTime;
    private LocalDateTime finishTime;

    public static SchedulingTaskResponse from(SchedulingTask task) {
        return SchedulingTaskResponse.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .semester(task.getSemester())
                .weekCount(task.getWeekCount())
                .status(task.getStatus())
                .maxInitialTemp(task.getMaxInitialTemp())
                .minInitialTemp(task.getMinInitialTemp())
                .minTemp(task.getMinTemp())
                .coolingRate(task.getCoolingRate())
                .maxTempIterations(task.getMaxTempIterations())
                .neighborsPerTemp(task.getNeighborsPerTemp())
                .maxRepairAttempts(task.getMaxRepairAttempts())
                .randomSeed(task.getRandomSeed())
                .hardConstraintWeight(task.getHardConstraintWeight())
                .wTeacherPreference(task.getWTeacherPreference())
                .wCourseDistribution(task.getWCourseDistribution())
                .wStudentBalance(task.getWStudentBalance())
                .wTeacherContinuous(task.getWTeacherContinuous())
                .wStudentIdle(task.getWStudentIdle())
                .wMorningEvening(task.getWMorningEvening())
                .createTime(task.getCreateTime())
                .finishTime(task.getFinishTime())
                .build();
    }
}
