package com.scheduling.repository;

import com.scheduling.entity.TeacherPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 教师时间偏好 Repository
 *
 * 服务于算法数据组装(S1 教师时间偏好): ExecutionService 只按任务实际涉及的
 * 教师 ID 集合查询一次, 再转换为 Map&lt;teacherId, Map&lt;timeSlotId, level&gt;&gt; 交给算法层。
 */
@Repository
public interface TeacherPreferenceRepository extends JpaRepository<TeacherPreference, Long> {

    /** 按教师 ID 集合批量查询(任务涉及的全部教师一次取回, 避免 N+1) */
    List<TeacherPreference> findByTeacherIdIn(Collection<Long> teacherIds);
}
