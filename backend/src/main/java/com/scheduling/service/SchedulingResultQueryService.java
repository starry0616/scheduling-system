package com.scheduling.service;

import com.scheduling.dto.ScheduleEntryView;
import com.scheduling.dto.SchedulingRunResponse;
import com.scheduling.entity.Classroom;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.Course;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.ScheduleEntry;
import com.scheduling.entity.ScheduleEntryClass;
import com.scheduling.entity.SchedulingResult;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.entity.Teacher;
import com.scheduling.entity.TimeSlot;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ClassroomRepository;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingRepository;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.ScheduleEntryClassRepository;
import com.scheduling.repository.ScheduleEntryRepository;
import com.scheduling.repository.SchedulingResultRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import com.scheduling.repository.TeacherRepository;
import com.scheduling.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排课结果/条目查询服务(阶段七前端接入, 只读)
 *
 * 提供:
 *   - 按任务查结果统计摘要(复用 SchedulingRunResponse 结构);
 *   - 按结果ID查完整课表条目视图(含课程/教师/教室/班级/时间上下文)。
 *
 * 仅新增只读能力, 不触碰算法/落库/状态迁移逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingResultQueryService {

    private final SchedulingResultRepository resultRepository;
    private final SchedulingTaskRepository taskRepository;
    private final ScheduleEntryRepository entryRepository;
    private final ScheduleEntryClassRepository entryClassRepository;
    private final CourseOfferingRepository offeringRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final CourseRepository courseRepository;
    private final TeacherRepository teacherRepository;
    private final ClassroomRepository classroomRepository;
    private final ClazzRepository clazzRepository;

    /** 按任务查询结果统计摘要; 任务未执行/无结果时抛 404 */
    @Transactional(readOnly = true)
    public SchedulingRunResponse getSummaryByTask(Long taskId) {
        SchedulingResult result = resultRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(404, "该任务尚未执行, 暂无排课结果"));
        SchedulingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "排课任务不存在: id=" + taskId));

        int hardViolation = result.getHardViolationCount() == null ? 0 : result.getHardViolationCount();
        boolean feasible = hardViolation == 0;
        return SchedulingRunResponse.builder()
                .taskId(taskId)
                .resultId(result.getId())
                .status(task.getStatus())
                .outcome(feasible ? "FEASIBLE" : "BEST_EFFORT")
                .feasible(feasible)
                .hardViolation(hardViolation)
                .softPenalty(result.getSoftViolationCount() == null ? 0 : result.getSoftViolationCount())
                .energy(result.getBestFitness())
                .runtimeMs(result.getExecutionTimeMs())
                .iterations(result.getIterationCount())
                .seed(task.getRandomSeed())
                .hardConstraintWeight(task.getHardConstraintWeight())
                .build();
    }

    /** 按结果ID查询该结果对应的完整课表条目(按 entry id 升序, 顺序稳定) */
    @Transactional(readOnly = true)
    public List<ScheduleEntryView> getSchedule(Long resultId) {
        SchedulingResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(404, "排课结果不存在: id=" + resultId));
        List<ScheduleEntry> entries = entryRepository.findByTaskIdOrderByIdAsc(result.getTaskId());
        if (entries.isEmpty()) {
            return List.of();
        }

        List<Long> offeringIds = entries.stream().map(ScheduleEntry::getCourseOfferingId).distinct().toList();
        Map<Long, CourseOffering> offeringMap = offeringIds.isEmpty() ? Map.of()
                : offeringRepository.findAllById(offeringIds).stream()
                        .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a));

        Map<Long, Course> courseMap = resolveCourses(offeringMap.values());
        Map<Long, Teacher> teacherMap = resolveTeachers(offeringMap.values());

        List<Long> classroomIds = entries.stream().map(ScheduleEntry::getClassroomId).distinct().toList();
        Map<Long, Classroom> classroomMap = classroomIds.isEmpty() ? Map.of()
                : classroomRepository.findAllById(classroomIds).stream()
                        .collect(Collectors.toMap(Classroom::getId, Function.identity(), (a, b) -> a));

        List<Long> slotIds = entries.stream().map(ScheduleEntry::getTimeSlotId).distinct().toList();
        Map<Long, TimeSlot> slotMap = slotIds.isEmpty() ? Map.of()
                : timeSlotRepository.findAllById(slotIds).stream()
                        .collect(Collectors.toMap(TimeSlot::getId, Function.identity(), (a, b) -> a));

        List<Long> entryIds = entries.stream().map(ScheduleEntry::getId).toList();
        Map<Long, List<ScheduleEntryClass>> classLinksByEntry = entryClassRepository
                .findByScheduleEntryIdInOrderByIdAsc(entryIds).stream()
                .collect(Collectors.groupingBy(ScheduleEntryClass::getScheduleEntryId,
                        LinkedHashMap::new, Collectors.toList()));
        List<Long> classIds = classLinksByEntry.values().stream()
                .flatMap(List::stream).map(ScheduleEntryClass::getClassId).distinct().toList();
        Map<Long, Clazz> clazzMap = classIds.isEmpty() ? Map.of()
                : clazzRepository.findAllById(classIds).stream()
                        .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));

        return entries.stream().map(entry -> {
            CourseOffering offering = offeringMap.get(entry.getCourseOfferingId());
            if (offering == null) {
                log.warn("课表条目引用的开课实例缺失, 跳过展示: entryId={}, offeringId={}",
                        entry.getId(), entry.getCourseOfferingId());
                return null;
            }
            TimeSlot slot = slotMap.get(entry.getTimeSlotId());
            Course course = courseMap.get(offering.getCourseId());
            Teacher teacher = teacherMap.get(offering.getTeacherId());
            Classroom classroom = classroomMap.get(entry.getClassroomId());
            List<ScheduleEntryView.ClassBrief> classes = toClassBriefs(
                    classLinksByEntry.get(entry.getId()), clazzMap);
            return ScheduleEntryView.builder()
                    .entryId(entry.getId())
                    .taskId(result.getTaskId())
                    .offeringId(entry.getCourseOfferingId())
                    .unitIndex(entry.getUnitIndex())
                    .durationSlots(offering.getDurationSlots())
                    .isLabCourse(offering.getIsLabCourse() != null && offering.getIsLabCourse() == 1)
                    .dayOfWeek(slot == null ? null : slot.getDayOfWeek())
                    .period(slot == null ? null : slot.getPeriod())
                    .startTime(slot == null ? null : slot.getStartTime())
                    .endTime(slot == null ? null : slot.getEndTime())
                    .courseId(course == null ? null : course.getId())
                    .courseCode(course == null ? null : course.getCourseCode())
                    .courseName(course == null ? null : course.getCourseName())
                    .courseType(course == null ? null : course.getCourseType())
                    .requiredRoomType(course == null ? null : course.getRequiredRoomType())
                    .teacherId(teacher == null ? null : teacher.getId())
                    .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                    .teacherName(teacher == null ? null : teacher.getName())
                    .classroomId(classroom == null ? null : classroom.getId())
                    .roomNo(classroom == null ? null : classroom.getRoomNo())
                    .building(classroom == null ? null : classroom.getBuilding())
                    .roomType(classroom == null ? null : classroom.getRoomType())
                    .classes(classes)
                    .build();
        }).filter(Objects::nonNull).toList();
    }

    private List<ScheduleEntryView.ClassBrief> toClassBriefs(List<ScheduleEntryClass> links,
                                                             Map<Long, Clazz> clazzMap) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .map(link -> clazzMap.get(link.getClassId()))
                .filter(Objects::nonNull)
                .map(clazz -> ScheduleEntryView.ClassBrief.builder()
                        .id(clazz.getId())
                        .className(clazz.getClassName())
                        .grade(clazz.getGrade())
                        .build())
                .toList();
    }

    private Map<Long, Course> resolveCourses(java.util.Collection<CourseOffering> offerings) {
        List<Long> courseIds = offerings.stream()
                .map(CourseOffering::getCourseId).filter(Objects::nonNull).distinct().toList();
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, Teacher> resolveTeachers(java.util.Collection<CourseOffering> offerings) {
        List<Long> teacherIds = offerings.stream()
                .map(CourseOffering::getTeacherId).filter(Objects::nonNull).distinct().toList();
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
    }
}
