package com.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源不可用时间实体 - 对应 resource_unavailability 表 (V2.2 §4)
 *
 * 领域语义: 表达"某资源在某时间段不可被排课", 是候选生成阶段的结构性硬过滤数据。
 *   - resource_type: TEACHER / CLASS / CLASSROOM(与 schema.sql 一致);
 *   - resource_id  : 对应 teacher.id / class.id / classroom.id;
 *   - time_slot_id : 不可用的时间段(整段, durationSlots 候选连续段只要覆盖任一即被过滤);
 *   - 注意: 本表无 task_id 列 —— 不可用时间为"系统级、跨任务"数据, 不做按任务的查询。
 *
 * 查询职责(服务算法数据组装):
 *   ExecutionService 只按任务实际涉及的资源集合执行三类查询:
 *     TEACHER   + 教师ID集合
 *     CLASS     + 班级ID集合
 *     CLASSROOM + 教室池ID集合
 *   转换为算法层 Map&lt;resourceId, Set&lt;timeSlotId&gt;&gt; 后交给 CandidateBuilder,
 *   本实体/Repository 绝不进入算法核心。
 */
@Entity
@Table(name = "resource_unavailability",
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_slot",
                columnNames = {"resource_type", "resource_id", "time_slot_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 资源类型: TEACHER / CLASS / CLASSROOM */
    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    /** 资源ID(teacher.id / class.id / classroom.id) */
    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    /** 不可用时间段ID(time_slot.id) */
    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    /** 不可用原因(可选) */
    @Column(length = 200)
    private String reason;
}
