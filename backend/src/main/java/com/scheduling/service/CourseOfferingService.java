package com.scheduling.service;

import com.scheduling.dto.CourseOfferingRequest;
import com.scheduling.dto.CourseOfferingResponse;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.Teacher;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.CourseOfferingClassRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.SchedulingTaskCourseRepository;
import com.scheduling.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 开课实例服务 (第3步第三阶段 3C-1)
 *
 * 领域语义: CourseOffering = 某学期 + 某教师 + 某课程 + 排课属性 的具体教学实例,
 * 是后续排课算法生成 SchedulingUnit 的输入。本阶段只做基础 CRUD:
 *   - course_id / teacher_id 引用资源必须存在(引用缺失 -> code404, 同 Teacher 模块风格)
 *   - weeklySessions / durationSlots 必须为正整数(由 DTO 校验)
 *   - isLabCourse 为派生字段: durationSlots > 1 即实验课
 *
 * ⚠ 本类不写任何排课/连续性逻辑; durationSlots>1 的连续时间段判断
 *   统一由 TimeSlot 的 (dayOfWeek, period) 推导, 禁止 slotId+1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final CourseOfferingClassRepository courseOfferingClassRepository;
    private final SchedulingTaskCourseRepository schedulingTaskCourseRepository;

    /** 开课实例列表, 可按学期精确过滤(空条件返回全部), 新数据在前 */
    public List<CourseOfferingResponse> list(String semester) {
        List<CourseOffering> offerings;
        if (semester == null || semester.isBlank()) {
            offerings = courseOfferingRepository.findAllByOrderByIdDesc();
        } else {
            offerings = courseOfferingRepository.findBySemesterOrderByIdDesc(semester.trim());
        }
        return offerings.stream().map(this::toResponse).toList();
    }

    /** 开课实例详情 */
    public CourseOfferingResponse getById(Long id) {
        return toResponse(getEntity(id));
    }

    /** 新增开课实例 */
    @Transactional
    public CourseOfferingResponse create(CourseOfferingRequest request) {
        Course course = requireCourse(request.getCourseId());
        Teacher teacher = requireTeacher(request.getTeacherId());
        CourseOffering offering = new CourseOffering();
        apply(offering, request);
        courseOfferingRepository.save(offering);
        log.info("新增开课实例成功: id={}, semester={}, courseId={}, teacherId={}",
                offering.getId(), offering.getSemester(), offering.getCourseId(), offering.getTeacherId());
        return toResponse(offering, course, teacher);
    }

    /** 修改开课实例 */
    @Transactional
    public CourseOfferingResponse update(Long id, CourseOfferingRequest request) {
        CourseOffering offering = getEntity(id);
        Course course = requireCourse(request.getCourseId());
        Teacher teacher = requireTeacher(request.getTeacherId());
        apply(offering, request);
        courseOfferingRepository.save(offering);
        log.info("修改开课实例成功: id={}", id);
        return toResponse(offering, course, teacher);
    }

    /**
     * 删除开课实例
     * 3C-2 起: 本库 course_offering_class 表由 JPA 自动维护(不含 DB ON DELETE CASCADE),
     * 为与 schema.sql 中 offering 侧 CASCADE 的意图保持一致, 先显式清理关联行再删除主数据;
     * 3C-3 起: 本库无 DB 外键, schema.sql 中 scheduling_task_course 的 offering 侧 RESTRICT
     * 语义由 existsByCourseOfferingId 显式检查实现, 不再依赖 DataIntegrityViolationException。
     */
    @Transactional
    public void delete(Long id) {
        CourseOffering offering = getEntity(id);
        if (schedulingTaskCourseRepository.existsByCourseOfferingId(id)) {
            throw new BusinessException(400, "该开课实例已被排课任务引用，无法删除");
        }
        courseOfferingClassRepository.deleteByCourseOfferingId(id);
        courseOfferingRepository.delete(offering);
        log.info("删除开课实例成功: id={}", id);
    }

    private void apply(CourseOffering offering, CourseOfferingRequest request) {
        int durationSlots = request.getDurationSlots() == null ? 1 : request.getDurationSlots();
        offering.setCourseId(request.getCourseId());
        offering.setTeacherId(request.getTeacherId());
        offering.setSemester(request.getSemester().trim());
        offering.setWeeklySessions(request.getWeeklySessions());
        offering.setDurationSlots(durationSlots);
        offering.setIsLabCourse(durationSlots > 1 ? 1 : 0);
    }

    private Course requireCourse(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(404, "引用的课程不存在: id=" + courseId));
    }

    private Teacher requireTeacher(Long teacherId) {
        return teacherRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessException(404, "引用的教师不存在: id=" + teacherId));
    }

    private CourseOffering getEntity(Long id) {
        return courseOfferingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "开课实例不存在: id=" + id));
    }

    /** 组装响应(冗余课程/教师展示名, 资源可能已删除则置空, 不影响主数据返回) */
    private CourseOfferingResponse toResponse(CourseOffering offering) {
        Course course = courseRepository.findById(offering.getCourseId()).orElse(null);
        Teacher teacher = teacherRepository.findById(offering.getTeacherId()).orElse(null);
        return toResponse(offering, course, teacher);
    }

    private CourseOfferingResponse toResponse(CourseOffering offering, Course course, Teacher teacher) {
        return CourseOfferingResponse.from(offering, course, teacher);
    }
}
