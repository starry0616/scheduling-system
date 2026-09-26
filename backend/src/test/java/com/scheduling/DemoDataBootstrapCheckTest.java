package com.scheduling;

import com.scheduling.common.Constants;
import com.scheduling.entity.SchedulingTask;
import com.scheduling.repository.CourseRepository;
import com.scheduling.repository.SchedulingTaskRepository;
import com.scheduling.service.DemoDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demo 数据真实启动路径验收 (8.3-S 第一批 C2)
 *
 * <p>不使用 test profile, 而是走<b>生产配置路径</b>:
 * 加载主 application.yml(app.demo-data.enabled=true), 因此应用启动时的
 * {@code DemoDataInitializer}(ApplicationRunner) 会在 context 启动阶段自动执行。
 *
 * <p>@DirtiesContext(classMode = BEFORE_EACH_TEST_METHOD) 使每个用例都拥有一个
 * 全新 Spring context —— 等价于<b>连续重启应用两次</b>, 用于验证:
 * <ol>
 *   <li>第 1 次启动: 数据库从未初始化演示数据时, 自动插入完整 Demo 数据并创建 PENDING 演示任务;</li>
 *   <li>第 2 次启动: 演示标记课程已存在, 初始化<b>整体跳过</b> ——
 *       不产生重复数据、不产生唯一键冲突、不破坏既有数据;</li>
 *   <li>启动后任意时刻再调用 {@code DemoDataService.initializeIfNeeded()} 同样返回 false(幂等)。</li>
 * </ol>
 *
 * <p>注意: 本用例会把 Demo 数据<b>真实写入</b>开发库(与答辩演示环境一致);
 * 若不需要可设置 app.demo-data.enabled=false 后删除演示数据。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DemoDataBootstrapCheckTest {

    @Autowired private DemoDataService demoDataService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SchedulingTaskRepository schedulingTaskRepository;

    private void assertDemoDataReady() {
        // 演示数据必须已完整就绪
        assertTrue(courseRepository.existsByCourseCode(DemoDataService.MARKER_COURSE_CODE),
                "演示标记课程应存在");
        SchedulingTask demoTask = schedulingTaskRepository.findAll().stream()
                .filter(t -> DemoDataService.DEMO_TASK_NAME.equals(t.getTaskName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("启动后应存在演示任务"));
        assertEquals(DemoDataService.DEMO_SEMESTER, demoTask.getSemester());
        assertEquals(Constants.TASK_PENDING, demoTask.getStatus(),
                "演示任务应保持 PENDING(供答辩现场点击开始排课)");
    }

    @Test
    @DisplayName("真实启动#1: context 启动自动完成 Demo 初始化")
    void firstStartupAutoInitializes() {
        assertDemoDataReady();
        // 启动后再手动触发也必须幂等跳过
        assertFalse(demoDataService.initializeIfNeeded(),
                "已初始化完成后, 任何再次调用都必须整体跳过");
    }

    @Test
    @DisplayName("真实启动#2: 重启应用不产生任何重复 Demo 数据")
    void secondStartupDoesNotDuplicate() {
        assertDemoDataReady();
        assertFalse(demoDataService.initializeIfNeeded(),
                "第二次启动的初始化必须整体跳过");
        // 重复任务恰好 1 个(无重复插入 / 无唯一键冲突 —— 否则 context 启动即抛异常)
        long demoTaskCount = schedulingTaskRepository.findAll().stream()
                .filter(t -> DemoDataService.DEMO_TASK_NAME.equals(t.getTaskName()))
                .count();
        assertEquals(1, demoTaskCount, "重启后演示任务必须仍为 1 个");
    }
}
