package com.scheduling.service;

import com.scheduling.dto.ClazzRequest;
import com.scheduling.dto.ClazzResponse;
import com.scheduling.entity.Clazz;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 班级基础管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClazzService {

    private final ClazzRepository clazzRepository;
    private final CourseOfferingClassRepository courseOfferingClassRepository;

    /** 班级列表, 支持按班级名称/年级模糊查询 */
    public List<ClazzResponse> list(String keyword) {
        List<Clazz> classes;
        if (keyword == null || keyword.isBlank()) {
            classes = clazzRepository.findAllByOrderByIdDesc();
        } else {
            classes = clazzRepository.findByClassNameContainingOrGradeContainingOrderByIdDesc(keyword.trim(), keyword.trim());
        }
        return classes.stream().map(ClazzResponse::from).toList();
    }

    /** 班级详情 */
    public ClazzResponse getById(Long id) {
        return ClazzResponse.from(getEntity(id));
    }

    /** 新增班级 */
    @Transactional
    public ClazzResponse create(ClazzRequest request) {
        if (clazzRepository.existsByClassName(request.getClassName().trim())) {
            throw new BusinessException(400, "班级名称已存在: " + request.getClassName());
        }
        Clazz clazz = Clazz.builder()
                .className(request.getClassName().trim())
                .grade(request.getGrade())
                .studentCount(request.getStudentCount())
                .department(request.getDepartment())
                .build();
        clazzRepository.save(clazz);
        log.info("新增班级成功: {}", clazz.getClassName());
        return ClazzResponse.from(clazz);
    }

    /** 修改班级 */
    @Transactional
    public ClazzResponse update(Long id, ClazzRequest request) {
        Clazz clazz = getEntity(id);
        clazzRepository.findByClassName(request.getClassName().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException(400, "班级名称已存在: " + request.getClassName());
                });

        clazz.setClassName(request.getClassName().trim());
        clazz.setGrade(request.getGrade());
        clazz.setStudentCount(request.getStudentCount());
        clazz.setDepartment(request.getDepartment());
        clazzRepository.save(clazz);
        log.info("修改班级成功: id={}", id);
        return ClazzResponse.from(clazz);
    }

    /** 删除班级 */
    @Transactional
    public void delete(Long id) {
        Clazz clazz = getEntity(id);
        // 3C-2 起: 本库表由 JPA 自动维护(无 DB 外键), 被开课实例关联的引用保护需在服务层显式完成
        if (courseOfferingClassRepository.existsByClassId(id)) {
            throw new BusinessException(400, "该班级已被学生或开课实例引用，无法删除");
        }
        try {
            clazzRepository.delete(clazz);
            log.info("删除班级成功: id={}", id);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, "该班级已被学生或开课实例引用，无法删除");
        }
    }

    private Clazz getEntity(Long id) {
        return clazzRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "班级不存在: id=" + id));
    }
}
