package com.scheduling.repository;

import com.scheduling.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 教师 Repository
 */
@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByTeacherNo(String teacherNo);

    Optional<Teacher> findByUserId(Long userId);

    boolean existsByTeacherNo(String teacherNo);

    /** 全量列表, 新数据在前 */
    List<Teacher> findAllByOrderByIdDesc();

    /** 按工号/姓名模糊查询 */
    List<Teacher> findByTeacherNoContainingOrNameContainingOrderByIdDesc(String teacherNo, String name);
}
