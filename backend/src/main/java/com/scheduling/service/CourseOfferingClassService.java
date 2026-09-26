package com.scheduling.service;

import com.scheduling.dto.CourseOfferingClassesRequest;
import com.scheduling.dto.ClazzResponse;
import com.scheduling.entity.Clazz;
import com.scheduling.entity.CourseOffering;
import com.scheduling.entity.CourseOfferingClass;
import com.scheduling.exception.BusinessException;
import com.scheduling.repository.ClazzRepository;
import com.scheduling.repository.CourseOfferingClassRepository;
import com.scheduling.repository.CourseOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 开课实例-班级关联服务 (第3步第三阶段 3C-2)
 *
 * 领域语义: 一个 CourseOffering 可关联多个班级(合班上课), 班级集合是后续
 * SchedulingUnit 计算 totalStudents(各班级人数求和) 与班级侧时间冲突检查的输入。
 *
 * 接口语义:
 *   - 查询: 返回某开课实例当前关联的全部班级(按关联建立顺序, 即 id 升序);
 *   - 保存: 覆盖式(整体替换) —— 传入本次期望的完整 classIds 集合,
 *     事务内先删旧关联再插入新关联, 保证幂等; 允许空集合表示清空关联;
 *     classIds 中重复 id 自动去重, 引用班级必须存在(不存在 -> code404, 与引用资源校验风格一致)。
 *
 * 删除语义: 删除开课实例时由 CourseOfferingService 先删除本表关联行再删主数据,
 * 与 schema.sql 中 course_offering 侧 ON DELETE CASCADE 的意图保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOfferingClassService {

    private final CourseOfferingClassRepository courseOfferingClassRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ClazzRepository clazzRepository;

    /** 查询某开课实例已关联的班级列表(offering 不存在 -> code404), 按关联建立顺序返回 */
    public List<ClazzResponse> listClasses(Long courseOfferingId) {
        requireOffering(courseOfferingId);
        List<CourseOfferingClass> links = courseOfferingClassRepository
                .findByCourseOfferingIdOrderByIdAsc(courseOfferingId);
        return toResponses(links);
    }

    /** 覆盖式保存关联班级(事务内先删后插, 幂等); 允许空=清空; 返回保存后的班级列表 */
    @Transactional
    public List<ClazzResponse> replaceClasses(Long courseOfferingId, CourseOfferingClassesRequest request) {
        requireOffering(courseOfferingId);
        List<Long> rawIds = request.getClassIds();

        // null 元素是脏数据, 明确拒绝而非静默忽略
        if (rawIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(400, "关联班级ID不能为空");
        }
        // 去重, 保持首次出现顺序(LinkedHashSet)
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(rawIds));

        if (!distinctIds.isEmpty()) {
            // 引用的班级必须全部存在, 否则整体拒绝(避免部分成功造成半更新)
            Map<Long, Clazz> classMap = clazzRepository.findAllById(distinctIds).stream()
                    .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
            for (Long classId : distinctIds) {
                if (!classMap.containsKey(classId)) {
                    throw new BusinessException(404, "引用的班级不存在: id=" + classId);
                }
            }
        }

        // 先删旧关联, 再插入新关联 —— 同一事务内整体替换, 天然幂等
        courseOfferingClassRepository.deleteByCourseOfferingId(courseOfferingId);
        if (!distinctIds.isEmpty()) {
            List<CourseOfferingClass> links = distinctIds.stream()
                    .map(classId -> CourseOfferingClass.builder()
                            .courseOfferingId(courseOfferingId)
                            .classId(classId)
                            .build())
                    .toList();
            courseOfferingClassRepository.saveAll(links);
        }
        log.info("保存开课实例-班级关联成功: courseOfferingId={}, classCount={}", courseOfferingId, distinctIds.size());

        return listClasses(courseOfferingId);
    }

    // ---------- 内部方法 ----------

    private CourseOffering requireOffering(Long courseOfferingId) {
        return courseOfferingRepository.findById(courseOfferingId)
                .orElseThrow(() -> new BusinessException(404, "开课实例不存在: id=" + courseOfferingId));
    }

    /** 按关联顺序把班级行映射为响应(班级理论上必存在; 若异常缺失则跳过并保持顺序稳定) */
    private List<ClazzResponse> toResponses(List<CourseOfferingClass> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> classIds = links.stream().map(CourseOfferingClass::getClassId).toList();
        Map<Long, Clazz> classMap = clazzRepository.findAllById(classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
        return links.stream()
                .map(link -> classMap.get(link.getClassId()))
                .filter(Objects::nonNull)
                .map(ClazzResponse::from)
                .toList();
    }
}
