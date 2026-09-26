package com.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 排课任务实体 - 对应 scheduling_task 表 (第3步第三阶段 3C-3)
 *
 * 领域语义: SchedulingTask 是一次自动排课请求的"任务壳", 描述排课范围 + 算法参数。
 *   - 排课范围: 通过 scheduling_task_course(纳入哪些开课实例) 与
 *     scheduling_task_classroom(可用教室池) 两张关联表表达(本阶段一并实现);
 *   - SA 算法参数/软约束权重: 本阶段仅存取与校验, 由排课执行阶段消费;
 *   - hardConstraintWeight: 系统字段, 创建恒为 null, 严禁客户端提交。
 *     后续在任务范围完整、进入排课执行前按 W_hard = SoftMax + 1 计算并回填
 *     (本阶段不引入任何算法/能量计算逻辑, null = "待系统自动计算")。
 *
 * 状态约定(schema.sql status DEFAULT 'PENDING'):
 *   PENDING   可修改/可删除/可改范围
 *   RUNNING   禁止修改/删除/改范围(后续执行阶段由 run 置入)
 *   COMPLETED 禁止修改/删除/改范围(执行阶段结束置入)
 *   FAILED    按非 PENDING 处理, 禁止普通修改/删除(重新执行语义后续阶段设计)
 *   本阶段没有任何状态迁移接口, 任务创建后固定为 PENDING。
 *
 * 说明: 本库表由 JPA ddl-auto=update 自动维护, 不含 DB 外键与列默认值注释,
 *       数值默认值由服务层在创建时写入; CASCADE/RESTRICT 语义由服务层显式保证。
 */
@Entity
@Table(name = "scheduling_task")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务名称 */
    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    /** 排课学期, 如: 2026秋(任务只能纳入同学期开课实例) */
    @Column(nullable = false, length = 20)
    private String semester;

    /** 总周数, 与数据库 DEFAULT 16 保持一致 */
    @Builder.Default
    @Column(name = "week_count", nullable = false)
    private Integer weekCount = 16;

    /** 任务状态: PENDING/RUNNING/COMPLETED/FAILED, 与数据库 DEFAULT 'PENDING' 保持一致 */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    // ---------- SA 算法参数 ----------

    /** 初始温度上限, 与数据库 DEFAULT 1000.0 保持一致 */
    @Builder.Default
    @Column(name = "max_initial_temp", nullable = false)
    private Double maxInitialTemp = 1000.0;

    /** 初始温度下限, 与数据库 DEFAULT 10.0 保持一致 */
    @Builder.Default
    @Column(name = "min_initial_temp", nullable = false)
    private Double minInitialTemp = 10.0;

    /** 终止温度, 与数据库 DEFAULT 0.1 保持一致 */
    @Builder.Default
    @Column(name = "min_temp", nullable = false)
    private Double minTemp = 0.1;

    /** 降温系数, 与数据库 DEFAULT 0.95 保持一致 */
    @Builder.Default
    @Column(name = "cooling_rate", nullable = false)
    private Double coolingRate = 0.95;

    /** 温度迭代次数(外循环), 与数据库 DEFAULT 5000 保持一致 */
    @Builder.Default
    @Column(name = "max_temp_iterations", nullable = false)
    private Integer maxTempIterations = 5000;

    /** 每温度邻域评价次数(内循环), 与数据库 DEFAULT 20 保持一致 */
    @Builder.Default
    @Column(name = "neighbors_per_temp", nullable = false)
    private Integer neighborsPerTemp = 20;

    /** 冲突修复最大轮数, 与数据库 DEFAULT 3 保持一致 */
    @Builder.Default
    @Column(name = "max_repair_attempts", nullable = false)
    private Integer maxRepairAttempts = 3;

    /** 随机种子(null = 系统时间) */
    @Column(name = "random_seed")
    private Long randomSeed;

    // ---------- 权重 ----------

    /**
     * 硬约束权重(系统字段): 创建恒为 null, 客户端禁止提交;
     * 后续按实际 SchedulingProblem 规模计算 W_hard = SoftMax + 1 后回填。
     */
    @Column(name = "hard_constraint_weight")
    private Long hardConstraintWeight;

    /** S1 教师时间偏好权重, 与数据库 DEFAULT 50 保持一致 */
    @Builder.Default
    @Column(name = "w_teacher_preference", nullable = false)
    private Integer wTeacherPreference = 50;

    /** S2 日期分布均衡度权重, 与数据库 DEFAULT 30 保持一致 */
    @Builder.Default
    @Column(name = "w_course_distribution", nullable = false)
    private Integer wCourseDistribution = 30;

    /** S3 学生每天课量均衡权重, 与数据库 DEFAULT 25 保持一致 */
    @Builder.Default
    @Column(name = "w_student_balance", nullable = false)
    private Integer wStudentBalance = 25;

    /** S4 教师连续课节权重, 与数据库 DEFAULT 30 保持一致 */
    @Builder.Default
    @Column(name = "w_teacher_continuous", nullable = false)
    private Integer wTeacherContinuous = 30;

    /** S5 学生空课时间权重, 与数据库 DEFAULT 25 保持一致 */
    @Builder.Default
    @Column(name = "w_student_idle", nullable = false)
    private Integer wStudentIdle = 25;

    /** S6 早晚节避免权重, 与数据库 DEFAULT 15 保持一致 */
    @Builder.Default
    @Column(name = "w_morning_evening", nullable = false)
    private Integer wMorningEvening = 15;

    /** 创建时间 */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 完成时间(执行阶段回填, 创建时为 null) */
    @Column(name = "finish_time")
    private LocalDateTime finishTime;
}
