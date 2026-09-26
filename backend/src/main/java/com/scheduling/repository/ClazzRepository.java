package com.scheduling.repository;

import com.scheduling.entity.Clazz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 班级 Repository
 */
@Repository
public interface ClazzRepository extends JpaRepository<Clazz, Long> {

    Optional<Clazz> findByClassName(String className);

    boolean existsByClassName(String className);

    /** 全量列表, 新数据在前 */
    List<Clazz> findAllByOrderByIdDesc();

    /** 按班级名称/年级模糊查询 */
    List<Clazz> findByClassNameContainingOrGradeContainingOrderByIdDesc(String className, String grade);
}
