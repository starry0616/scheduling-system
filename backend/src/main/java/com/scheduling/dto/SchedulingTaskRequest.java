package com.scheduling.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 排课任务新增/修改请求 (第3步第三阶段 3C-3)
 *
 * 语义说明:
 *   - 不含 status / hardConstraintWeight 两个系统字段:
 *       status 由服务层固定为 PENDING(本阶段无状态迁移);
 *       hardConstraintWeight 恒为 null(由后续执行阶段按 W_hard = SoftMax + 1 自动计算);
 *   - 数值参数允许缺省(为 null), 缺省时创建走系统默认值
 *     (weekCount=16 及 Constants 中默认 SA 参数/权重);
 *   - PUT 修改时数值参数为 null 表示"保持现值不修改", 因此可整体提交也可部分修改;
 *   - randomSeed 可为空(null=后续由系统时间决定);
 *   - 提供的数值全部按约束范围严格校验。
 */
@Data
public class SchedulingTaskRequest {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称长度不能超过100")
    private String taskName;

    @NotBlank(message = "学期不能为空")
    @Size(max = 20, message = "学期长度不能超过20")
    private String semester;

    /** 总周数, 缺省 16 */
    @Min(value = 1, message = "总周数必须大于等于1")
    private Integer weekCount;

    // ---------- SA 算法参数(缺省走默认值) ----------

    @DecimalMin(value = "0", inclusive = false, message = "初始温度上限必须大于0")
    private Double maxInitialTemp;

    @DecimalMin(value = "0", inclusive = false, message = "初始温度下限必须大于0")
    private Double minInitialTemp;

    @DecimalMin(value = "0", inclusive = false, message = "终止温度必须大于0")
    private Double minTemp;

    @DecimalMin(value = "0", inclusive = false, message = "降温系数必须大于0且小于1")
    @DecimalMax(value = "1", inclusive = false, message = "降温系数必须大于0且小于1")
    private Double coolingRate;

    @Min(value = 1, message = "温度迭代次数必须大于等于1")
    private Integer maxTempIterations;

    @Min(value = 1, message = "每温度邻域评价次数必须大于等于1")
    private Integer neighborsPerTemp;

    @Min(value = 0, message = "冲突修复最大轮数必须大于等于0")
    private Integer maxRepairAttempts;

    /** 随机种子(null = 系统时间) */
    private Long randomSeed;

    // ---------- 软约束权重(缺省走默认值) ----------
    // 注意: getter 为 getWXxx, 按 Bean/Jackson 规则会被 mangle 成大写 W 开头的属性名,
    //       统一显式 @JsonProperty 固定为小写驼峰命名。

    @JsonProperty("wTeacherPreference")
    @Min(value = 0, message = "S1教师时间偏好权重不能为负")
    private Integer wTeacherPreference;

    @JsonProperty("wCourseDistribution")
    @Min(value = 0, message = "S2日期分布均衡度权重不能为负")
    private Integer wCourseDistribution;

    @JsonProperty("wStudentBalance")
    @Min(value = 0, message = "S3学生每天课量均衡权重不能为负")
    private Integer wStudentBalance;

    @JsonProperty("wTeacherContinuous")
    @Min(value = 0, message = "S4教师连续课节权重不能为负")
    private Integer wTeacherContinuous;

    @JsonProperty("wStudentIdle")
    @Min(value = 0, message = "S5学生空课时间权重不能为负")
    private Integer wStudentIdle;

    @JsonProperty("wMorningEvening")
    @Min(value = 0, message = "S6早晚节避免权重不能为负")
    private Integer wMorningEvening;
}
