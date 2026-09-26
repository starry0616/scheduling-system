package com.scheduling.service;

import com.scheduling.dto.CourseRequest;
import com.scheduling.dto.CourseResponse;
import com.scheduling.entity.Course;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程基础管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseOfferingRepository courseOfferingRepository;

    /** 课程列表, 支持按课程代码/名称模糊查询 */
    public List<CourseResponse> list(String keyword) {
        List<Course> courses;
        if (keyword == null || keyword.isBlank()) {
            courses = courseRepository.findAllByOrderByIdDesc();
        } else {
            courses = courseRepository.findByCourseCodeContainingOrCourseNameContainingOrderByIdDesc(keyword.trim(), keyword.trim());
        }
        return courses.stream().map(CourseResponse::from).toList();
    }

    /** 课程详情 */
    public CourseResponse getById(Long id) {
        return CourseResponse.from(getEntity(id));
    }

    /** 新增课程 */
    @Transactional
    public CourseResponse create(CourseRequest request) {
        if (courseRepository.existsByCourseCode(request.getCourseCode().trim())) {
            throw new BusinessException(400, "课程代码已存在: " + request.getCourseCode());
        }
        Course course = Course.builder()
                .courseCode(request.getCourseCode().trim())
                .courseName(request.getCourseName().trim())
                .courseType(request.getCourseType())
                .requiredRoomType(request.getRequiredRoomType())
                .build();
        courseRepository.save(course);
        log.info("新增课程成功: {}", course.getCourseCode());
        return CourseResponse.from(course);
    }

    /** 修改课程 */
    @Transactional
    public CourseResponse update(Long id, CourseRequest request) {
        Course course = getEntity(id);

        // 课程代码唯一性(排除自身)
        courseRepository.findByCourseCode(request.getCourseCode().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException(400, "课程代码已存在: " + request.getCourseCode());
                });

        course.setCourseCode(request.getCourseCode().trim());
        course.setCourseName(request.getCourseName().trim());
        course.setCourseType(request.getCourseType());
        course.setRequiredRoomType(request.getRequiredRoomType());
        courseRepository.save(course);
        log.info("修改课程成功: id={}", id);
        return CourseResponse.from(course);
    }

    /**
     * 删除课程
     * 本库表由 JPA ddl-auto=update 自动维护(不含 DB 外键), schema.sql 中 course_offering
     * 的课程侧 RESTRICT 语义由 existsByCourseId 显式检查实现, 不再依赖
     * DataIntegrityViolationException(与 CourseOfferingService/ClassroomService 删除风格一致)。
     */
    @Transactional
    public void delete(Long id) {
        Course course = getEntity(id);
        if (courseOfferingRepository.existsByCourseId(id)) {
            throw new BusinessException(400, "该课程已被开课实例引用，无法删除");
        }
        courseRepository.delete(course);
        log.info("删除课程成功: id={}", id);
    }

    private Course getEntity(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "课程不存在: id=" + id));
    }
}
