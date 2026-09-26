package com.scheduling.repository;

import com.scheduling.entity.ResourceUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 资源不可用时间 Repository
 *
 * 服务于算法数据组装(候选生成的硬性时间过滤): 注意 schema 中本表没有 task_id 列,
 * 因此不提供任何"按任务"查询。ExecutionService 只按任务实际涉及的资源集合执行三类查询:
 * <pre>
 *   TEACHER   + 教师 ID 集合
 *   CLASS     + 班级 ID 集合
 *   CLASSROOM + 教室池 ID 集合
 * </pre>
 * 查询结果在 Service 层转换为 Map&lt;resourceId, Set&lt;timeSlotId&gt;&gt; 后交给算法层。
 */
@Repository
public interface ResourceUnavailabilityRepository extends JpaRepository<ResourceUnavailability, Long> {

    /** 按资源类型 + 资源 ID 集合查询(一次批量取回, 避免 N+1) */
    List<ResourceUnavailability> findByResourceTypeAndResourceIdIn(String resourceType,
                                                                   Collection<Long> resourceIds);
}
