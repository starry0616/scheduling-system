package com.scheduling.service;

import com.scheduling.common.Constants;
import com.scheduling.dto.TeacherRequest;
import com.scheduling.dto.TeacherResponse;
import com.scheduling.dto.UserCandidateResponse;
import com.scheduling.entity.Teacher;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师基础管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final CourseOfferingRepository courseOfferingRepository;

    /** 教师列表, 支持按工号/姓名模糊查询 */
    public List<TeacherResponse> list(String keyword) {
        List<Teacher> teachers;
        if (keyword == null || keyword.isBlank()) {
            teachers = teacherRepository.findAllByOrderByIdDesc();
        } else {
            teachers = teacherRepository.findByTeacherNoContainingOrNameContainingOrderByIdDesc(keyword.trim(), keyword.trim());
        }
        return teachers.stream().map(TeacherResponse::from).toList();
    }

    /** 教师详情 */
    public TeacherResponse getById(Long id) {
        return TeacherResponse.from(getEntity(id));
    }

    /**
     * 可绑定教师的候选登录账号(8.3-S 第二批 C1):
     * role=TEACHER 且 status=1 且尚未被任何教师记录绑定(user_id 唯一), 仅管理员可见。
     * currentTeacherId 非空时(编辑场景), 把当前教师已绑定的账号一并并入候选,
     * 供前端在"保持原账号绑定"与"改绑到其它未绑定账号"之间选择。
     */
    public List<UserCandidateResponse> findUserCandidates(Long currentTeacherId) {
        Set<Long> boundUserIds = teacherRepository.findAll().stream()
                .map(Teacher::getUserId)
                .collect(Collectors.toSet());
        if (currentTeacherId != null) {
            teacherRepository.findById(currentTeacherId)
                    .map(Teacher::getUserId)
                    .ifPresent(boundUserIds::remove);
        }
        return userRepository.findAll().stream()
                .filter(u -> Constants.ROLE_TEACHER.equals(u.getRole()))
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .filter(u -> !boundUserIds.contains(u.getId()))
                .map(UserCandidateResponse::from)
                .toList();
    }

    /** 新增教师 */
    @Transactional
    public TeacherResponse create(TeacherRequest request) {
        checkUserBindable(request.getUserId(), null);
        if (teacherRepository.existsByTeacherNo(request.getTeacherNo().trim())) {
            throw new BusinessException(400, "教师工号已存在: " + request.getTeacherNo());
        }
        Teacher teacher = Teacher.builder()
                .userId(request.getUserId())
                .teacherNo(request.getTeacherNo().trim())
                .name(request.getName().trim())
                .title(request.getTitle())
                .department(request.getDepartment())
                .build();
        teacherRepository.save(teacher);
        log.info("新增教师成功: {}", teacher.getTeacherNo());
        return TeacherResponse.from(teacher);
    }

    /** 修改教师 */
    @Transactional
    public TeacherResponse update(Long id, TeacherRequest request) {
        Teacher teacher = getEntity(id);
        checkUserBindable(request.getUserId(), id);
        teacherRepository.findByTeacherNo(request.getTeacherNo().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException(400, "教师工号已存在: " + request.getTeacherNo());
                });

        teacher.setUserId(request.getUserId());
        teacher.setTeacherNo(request.getTeacherNo().trim());
        teacher.setName(request.getName().trim());
        teacher.setTitle(request.getTitle());
        teacher.setDepartment(request.getDepartment());
        teacherRepository.save(teacher);
        log.info("修改教师成功: id={}", id);
        return TeacherResponse.from(teacher);
    }

    /**
     * 删除教师
     * 本库表由 JPA ddl-auto=update 自动维护(不含 DB 外键), schema.sql 中 course_offering
     * 的教师侧 RESTRICT 语义由 existsByTeacherId 显式检查实现, 不再依赖
     * DataIntegrityViolationException(与 CourseOfferingService/ClassroomService 删除风格一致)。
     */
    @Transactional
    public void delete(Long id) {
        Teacher teacher = getEntity(id);
        if (courseOfferingRepository.existsByTeacherId(id)) {
            throw new BusinessException(400, "该教师已被开课实例引用，无法删除");
        }
        teacherRepository.delete(teacher);
        log.info("删除教师成功: id={}", id);
    }

    /**
     * 校验 userId: 用户必须存在(满足外键), 且未被其它教师记录占用(user_id 唯一)
     */
    private void checkUserBindable(Long userId, Long currentTeacherId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "关联用户不存在: userId=" + userId));
        teacherRepository.findByUserId(userId)
                .filter(existing -> !existing.getId().equals(currentTeacherId))
                .ifPresent(existing -> {
                    throw new BusinessException(400, "该用户已绑定教师: 教师工号 " + existing.getTeacherNo());
                });
    }

    private Teacher getEntity(Long id) {
        return teacherRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "教师不存在: id=" + id));
    }
}
