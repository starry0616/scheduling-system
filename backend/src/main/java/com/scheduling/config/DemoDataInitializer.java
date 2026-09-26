package com.scheduling.config;

import com.scheduling.service.DemoDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 演示数据初始化入口 (8.3-S 第一批 C2)
 *
 * <p>在应用启动时调用 {@link DemoDataService#initializeIfNeeded()}, 仅当
 * 数据库尚未初始化过演示数据时插入一套稳定可重复的答辩演示数据(幂等, 详见服务类注释)。
 *
 * <p>开关: application.yml 的 {@code app.demo-data.enabled}:
 * <ul>
 *   <li>true(默认)  - 生产/演示环境启动时自动初始化;</li>
 *   <li>false       - 完全关闭(例如不希望演示数据进入已有业务数据的库时)。</li>
 * </ul>
 * 测试 profile(application-test.yml)显式置为 false, 避免测试共享库被自动写入;
 * DemoDataIntegrationTest 直接调用 DemoDataService 在事务内验证并回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private final DemoDataService demoDataService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            demoDataService.initializeIfNeeded();
        } catch (Exception e) {
            // 初始化失败不能阻断应用启动: 记录后由运维处置
            // (失败已由服务层单事务整体回滚, 不会残留半套数据, 下次启动会重试补齐)
            log.error("演示数据初始化失败(已回滚, 不影响应用启动): {}", e.getMessage(), e);
        }
    }
}
