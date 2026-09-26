package com.scheduling;

import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.Teacher;
import com.scheduling.entity.User;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.repository.UserRepository;
import com.scheduling.service.ClazzService;
import com.scheduling.service.CourseService;
import com.scheduling.service.TeacherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 删除引用保护测试 (8.3-S 第一批 D2)
 *
 * 目的: 课程/教师被 course_offering 引用时, 删除必须被服务层显式拒绝,
 * 不依赖数据库外键 / DataIntegrityViolationException(JPA ddl-auto=update 建表无 DB 外键)。
 *
 * 全部测试方法运行在事务内, 结束时整体回滚, 不污染共享测试库;
 * 业务错误由 BusinessException 统一承载(与 GlobalExceptionHandler 的 Result.error 语义一致)。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseTeacherDeleteGuardTest {

    @Autowired private CourseService courseService;
    @Autowired private TeacherService teacherService;
    @Autowired private ClazzService clazzService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeacherRepository teacherRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ClazzRepository clazzRepository;
    @Autowired private CourseOfferingRepository courseOfferingRepository;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static long seq = System.currentTimeMillis() % 1_000_000L;

    // ---------- 工具 ----------

    private String unique(String prefix) {
        return prefix + (seq++) + ((int) (Math.random() * 9000) + 1000);
    }

    private User newTeacherUser() {
        return userRepository.save(User.builder()
                .username(unique("du_"))
                .password(ENCODER.encode("guard123"))
                .realName("删除保护测试教师")
                .role("TEACHER")
                .status(1)
                .build());
    }

    private Teacher newTeacher(User user) {
        return teacherRepository.save(Teacher.builder()
                .userId(user.getId())
                .teacherNo(unique("dt_"))
                .name("删除保护测试教师")
                .title("讲师")
                .department("测试学院")
                .build());
    }

    private Course newCourse() {
        return courseRepository.save(Course.builder()
                .courseCode(unique("dc_"))
                .courseName("删除保护测试课程")
                .courseType("THEORY")
                .requiredRoomType("NORMAL")
                .build());
    }

    private CourseOffering link(Course course, Teacher teacher) {
        return courseOfferingRepository.save(CourseOffering.builder()
                .courseId(course.getId())
                .teacherId(teacher.getId())
                .semester("2026春")
                .weeklySessions(1)
                .durationSlots(1)
                .isLabCourse(0)
                .build());
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("D2-1: 未被 CourseOffering 引用的 Course 可以删除")
    void unreferencedCourseCanBeDeleted() {
        Course course = newCourse();
        courseService.delete(course.getId());
        assertFalse(courseRepository.existsByCourseCode(course.getCourseCode()),
                "删除后课程记录应不存在");
    }

    @Test
    @DisplayName("D2-2: 被 CourseOffering 引用的 Course 删除失败并返回业务错误")
    void referencedCourseCannotBeDeleted() {
        Course course = newCourse();
        Teacher teacher = newTeacher(newTeacherUser());
        link(course, teacher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.delete(course.getId()));
        assertEquals(400, ex.getCode(), "业务错误码应为 400");
        assertTrue(ex.getMessage().contains("该课程已被开课实例引用"),
                "错误信息应指明被开课实例引用, 实际: " + ex.getMessage());

        assertTrue(courseRepository.existsByCourseCode(course.getCourseCode()),
                "课程应仍保留");
    }

    @Test
    @DisplayName("D2-3: 未被 CourseOffering 引用的 Teacher 可以删除")
    void unreferencedTeacherCanBeDeleted() {
        Teacher teacher = newTeacher(newTeacherUser());
        teacherService.delete(teacher.getId());
        assertFalse(teacherRepository.existsByTeacherNo(teacher.getTeacherNo()),
                "删除后教师记录应不存在");
    }

    @Test
    @DisplayName("D2-4: 被 CourseOffering 引用的 Teacher 删除失败并返回业务错误")
    void referencedTeacherCannotBeDeleted() {
        Teacher teacher = newTeacher(newTeacherUser());
        Course course = newCourse();
        link(course, teacher);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> teacherService.delete(teacher.getId()));
        assertEquals(400, ex.getCode(), "业务错误码应为 400");
        assertTrue(ex.getMessage().contains("该教师已被开课实例引用"),
                "错误信息应指明被开课实例引用, 实际: " + ex.getMessage());

        assertTrue(teacherRepository.existsByTeacherNo(teacher.getTeacherNo()),
                "教师应仍保留");
    }

    @Test
    @DisplayName("D2-5: 课程被引用删除失败后, 删除引用的开课实例即可恢复删除能力(原有删除链路不受影响)")
    void deleteAllowedAfterOfferingRemoved() {
        Course course = newCourse();
        Teacher teacher = newTeacher(newTeacherUser());
        CourseOffering offering = link(course, teacher);

        assertThrows(BusinessException.class, () -> courseService.delete(course.getId()));
        assertThrows(BusinessException.class, () -> teacherService.delete(teacher.getId()));

        // 先删除开课实例(其自身校验: 未被排课任务引用时可删)
        courseOfferingRepository.delete(offering);
        courseOfferingRepository.flush();

        courseService.delete(course.getId());
        teacherService.delete(teacher.getId());
        assertFalse(courseRepository.existsByCourseCode(course.getCourseCode()));
        assertFalse(teacherRepository.existsByTeacherNo(teacher.getTeacherNo()));
    }

    @Test
    @DisplayName("D2-6: 未被开课实例引用的班级删除能力不受本次改动影响(既有 ClazzService 显式检查回归)")
    void clazzUnreferencedDeleteRegression() {
        Clazz clazz = clazzRepository.save(Clazz.builder()
                .className(unique("dcl_"))
                .grade("2024级")
                .studentCount(40)
                .department("测试学院")
                .build());
        clazzService.delete(clazz.getId());
        assertTrue(clazzRepository.findById(clazz.getId()).isEmpty(),
                "未引用的班级应可正常删除");
    }
}
