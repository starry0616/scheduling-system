package com.scheduling.repository;

import com.scheduling.entity.SchedulingTaskCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 排课任务-开课实例关联 Repository (第3步第三阶段 3C-3)
 */
@Repository
public interface SchedulingTaskCourseRepository extends JpaRepository<SchedulingTaskCourse, Long> {

    /** 某任务的全部关联(按关联 id 升序, 保证接口返回顺序稳定) */
    List<SchedulingTaskCourse> findBySchedulingTaskIdOrderByIdAsc(Long schedulingTaskId);

    /**
     * 删除某任务的全部关联(覆盖式保存与删除任务级联清理时使用)。
     * 必须使用 @Modifying 批量删除(立即执行): 若走派生 deleteBy 的逐实体删除,
     * 删除会延迟到事务 flush, 覆盖式保存时新插入会先于旧行删除触发唯一键冲突。
     */
    @Modifying
    @Query("delete from SchedulingTaskCourse c where c.schedulingTaskId = :taskId")
    void deleteBySchedulingTaskId(@Param("taskId") Long taskId);

    /** 某开课实例是否已被任一排课任务纳入(开课实例删除保护) */
    boolean existsByCourseOfferingId(Long courseOfferingId);
}
