package com.scheduling.repository;

import com.scheduling.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 课程 Repository
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCourseCode(String courseCode);

    boolean existsByCourseCode(String courseCode);

    /** 全量列表, 新数据在前 */
    List<Course> findAllByOrderByIdDesc();

    /** 按课程代码/名称模糊查询 */
    List<Course> findByCourseCodeContainingOrCourseNameContainingOrderByIdDesc(String courseCode, String courseName);
}
