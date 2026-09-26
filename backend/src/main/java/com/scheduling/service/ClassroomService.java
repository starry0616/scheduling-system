package com.scheduling.service;

import com.scheduling.dto.ClassroomRequest;
import com.scheduling.dto.ClassroomResponse;
import com.scheduling.entity.Classroom;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ClassroomRepository;
import com.scheduling.repository.SchedulingTaskClassroomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 教室基础管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    /** 合法教室类型 */
    private static final Set<String> ROOM_TYPES = Set.of("NORMAL", "MULTIMEDIA", "LAB");

    private final ClassroomRepository classroomRepository;
    private final SchedulingTaskClassroomRepository schedulingTaskClassroomRepository;

    /** 教室列表, 支持按教室编号/楼栋模糊查询 */
    public List<ClassroomResponse> list(String keyword) {
        List<Classroom> classrooms;
        if (keyword == null || keyword.isBlank()) {
            classrooms = classroomRepository.findAllByOrderByIdDesc();
        } else {
            classrooms = classroomRepository.findByRoomNoContainingOrBuildingContainingOrderByIdDesc(keyword.trim(), keyword.trim());
        }
        return classrooms.stream().map(ClassroomResponse::from).toList();
    }

    /** 教室详情 */
    public ClassroomResponse getById(Long id) {
        return ClassroomResponse.from(getEntity(id));
    }

    /** 新增教室 */
    @Transactional
    public ClassroomResponse create(ClassroomRequest request) {
        checkRoomType(request.getRoomType());
        if (classroomRepository.existsByRoomNo(request.getRoomNo().trim())) {
            throw new BusinessException(400, "教室编号已存在: " + request.getRoomNo());
        }
        Classroom classroom = Classroom.builder()
                .roomNo(request.getRoomNo().trim())
                .building(request.getBuilding())
                .capacity(request.getCapacity())
                .roomType(request.getRoomType())
                .build();
        classroomRepository.save(classroom);
        log.info("新增教室成功: {}", classroom.getRoomNo());
        return ClassroomResponse.from(classroom);
    }

    /** 修改教室 */
    @Transactional
    public ClassroomResponse update(Long id, ClassroomRequest request) {
        Classroom classroom = getEntity(id);
        checkRoomType(request.getRoomType());
        classroomRepository.findByRoomNo(request.getRoomNo().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException(400, "教室编号已存在: " + request.getRoomNo());
                });

        classroom.setRoomNo(request.getRoomNo().trim());
        classroom.setBuilding(request.getBuilding());
        classroom.setCapacity(request.getCapacity());
        classroom.setRoomType(request.getRoomType());
        classroomRepository.save(classroom);
        log.info("修改教室成功: id={}", id);
        return ClassroomResponse.from(classroom);
    }

    /**
     * 删除教室
     * 3C-3 起: 本库无 DB 外键, schema.sql 中 scheduling_task_classroom 的教室侧 RESTRICT
     * 语义由 existsByClassroomId 显式检查实现, 不再依赖 DataIntegrityViolationException。
     */
    @Transactional
    public void delete(Long id) {
        Classroom classroom = getEntity(id);
        if (schedulingTaskClassroomRepository.existsByClassroomId(id)) {
            throw new BusinessException(400, "该教室已被排课任务引用，无法删除");
        }
        classroomRepository.delete(classroom);
        log.info("删除教室成功: id={}", id);
    }

    private void checkRoomType(String roomType) {
        String type = roomType == null ? "" : roomType.trim().toUpperCase(Locale.ROOT);
        if (!ROOM_TYPES.contains(type)) {
            throw new BusinessException(400, "教室类型非法, 仅支持 NORMAL/MULTIMEDIA/LAB");
        }
    }

    private Classroom getEntity(Long id) {
        return classroomRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "教室不存在: id=" + id));
    }
}
