package com.scheduling.repository;

import com.scheduling.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 开课实例 Repository
 */
@Repository
public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    /** 全量列表, 新数据在前 */
    List<CourseOffering> findAllByOrderByIdDesc();

    /** 按学期精确过滤, 新数据在前 */
    List<CourseOffering> findBySemesterOrderByIdDesc(String semester);

    /** 某课程是否已被任一开课实例引用(课程删除保护: 引用存在时不允许删除) */
    boolean existsByCourseId(Long courseId);

    /** 某教师是否已被任一开课实例引用(教师删除保护: 引用存在时不允许删除) */
    boolean existsByTeacherId(Long teacherId);
}
