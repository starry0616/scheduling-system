package com.scheduling.repository;

import com.scheduling.entity.ScheduleEntryClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 排课条目-班级关联 Repository (V2.2 §19)
 *
 * 一条排课结果的授课班级集合; 与 entry 之间 N:1。
 */
@Repository
public interface ScheduleEntryClassRepository extends JpaRepository<ScheduleEntryClass, Long> {

    /** 按条目 ID 集合批量查询(按关联 id 升序) */
    List<ScheduleEntryClass> findByScheduleEntryIdInOrderByIdAsc(Collection<Long> scheduleEntryIds);

    /** 删除指定条目集合的全部班级关联(防御性清理, 批量立即执行) */
    @Modifying
    @Query("delete from ScheduleEntryClass c where c.scheduleEntryId in :scheduleEntryIds")
    void deleteByScheduleEntryIdIn(@Param("scheduleEntryIds") Collection<Long> scheduleEntryIds);
}
