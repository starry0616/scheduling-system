package com.scheduling.repository;

import com.scheduling.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 教室 Repository
 */
@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    Optional<Classroom> findByRoomNo(String roomNo);

    boolean existsByRoomNo(String roomNo);

    /** 全量列表, 新数据在前 */
    List<Classroom> findAllByOrderByIdDesc();

    /** 按教室编号/楼栋模糊查询 */
    List<Classroom> findByRoomNoContainingOrBuildingContainingOrderByIdDesc(String roomNo, String building);
}
