package com.scheduling.repository;

import com.scheduling.entity.CourseOfferingClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 开课实例-班级关联 Repository (第3步第三阶段 3C-2)
 */
@Repository
public interface CourseOfferingClassRepository extends JpaRepository<CourseOfferingClass, Long> {

    /** 某开课实例的全部关联(按关联 id 升序, 保证接口返回顺序稳定) */
    List<CourseOfferingClass> findByCourseOfferingIdOrderByIdAsc(Long courseOfferingId);

    /** 批量查询多个开课实例的全部关联(按关联 id 升序)。
     *  供"从排课任务装配完整数据"使用, 一次批量取回避免 N+1。 */
    List<CourseOfferingClass> findByCourseOfferingIdInOrderByIdAsc(Collection<Long> courseOfferingIds);

    /**
     * 删除某开课实例的全部关联(覆盖式保存与删除开课实例级联清理时使用)。
     * 必须使用 @Modifying 批量删除(立即执行): 若走派生 deleteBy 的逐实体删除,
     * 删除会延迟到事务 flush, 覆盖式保存时新插入会先于旧行删除触发唯一键冲突。
     */
    @Modifying
    @Query("delete from CourseOfferingClass c where c.courseOfferingId = :courseOfferingId")
    void deleteByCourseOfferingId(@Param("courseOfferingId") Long courseOfferingId);

    /** 某班级是否已被任一开课实例关联(班级删除保护: 引用存在时不允许删除) */
    boolean existsByClassId(Long classId);
}
