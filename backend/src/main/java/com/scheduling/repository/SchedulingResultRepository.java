package com.scheduling.repository;

import com.scheduling.entity.SchedulingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 排课结果 Repository (V2.2 §19)
 *
 * scheduling_result.task_id 唯一(任务与结果 1:1)。
 */
@Repository
public interface SchedulingResultRepository extends JpaRepository<SchedulingResult, Long> {

    Optional<SchedulingResult> findByTaskId(Long taskId);

    /**
     * 删除某任务的历史结果(防御性清理; 本阶段禁止重执行, 正常为空操作)。
     * 必须 @Modifying 批量删除立即执行, 避免与同事务新插入的顺序问题。
     */
    @Modifying
    @Query("delete from SchedulingResult r where r.taskId = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);
}
