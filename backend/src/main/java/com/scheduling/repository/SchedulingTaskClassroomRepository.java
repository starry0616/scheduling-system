package com.scheduling.repository;

import com.scheduling.entity.SchedulingTaskClassroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 排课任务-教室关联 Repository (第3步第三阶段 3C-3)
 */
@Repository
public interface SchedulingTaskClassroomRepository extends JpaRepository<SchedulingTaskClassroom, Long> {

    /** 某任务的可用教室关联(按关联 id 升序, 保证接口返回顺序稳定) */
    List<SchedulingTaskClassroom> findBySchedulingTaskIdOrderByIdAsc(Long schedulingTaskId);

    /**
     * 删除某任务的全部教室关联(覆盖式保存与删除任务级联清理时使用)。
     * 必须使用 @Modifying 批量删除, 理由同 SchedulingTaskCourseRepository。
     */
    @Modifying
    @Query("delete from SchedulingTaskClassroom c where c.schedulingTaskId = :taskId")
    void deleteBySchedulingTaskId(@Param("taskId") Long taskId);

    /** 某教室是否已被任一排课任务纳入(教室删除保护) */
    boolean existsByClassroomId(Long classroomId);
}
