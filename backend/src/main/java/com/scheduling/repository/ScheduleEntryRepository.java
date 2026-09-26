package com.scheduling.repository;

import com.scheduling.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 排课结果条目 Repository (V2.2 §19)
 *
 * 一个 SchedulingUnit(课次) = 一行 schedule_entry; 按任务 1:N。
 */
@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    /** 某任务的全部条目(按 entry id 升序, 顺序稳定) */
    List<ScheduleEntry> findByTaskIdOrderByIdAsc(Long taskId);

    /** 删除某任务的全部条目(防御性清理; 必须先删其下的 entry_class 关联行) */
    @Modifying
    @Query("delete from ScheduleEntry e where e.taskId = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);
}
