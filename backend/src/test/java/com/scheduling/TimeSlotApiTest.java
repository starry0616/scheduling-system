package com.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 时间段 Web 层测试 (第3步第二阶段)
 *
 * 重点验证时间段作为算法核心基础数据的正确性:
 *   - 25 个初始化时间段可查询, 且按 (dayOfWeek, period) 升序
 *   - 覆盖全部 5天×5大节 组合
 *   - 非法星期/非法节次被拒绝
 *   - 重复 (dayOfWeek, period) 被拒绝
 *   - 401/403
 */
@SpringBootTest
@AutoConfigureMockMvc
class TimeSlotApiTest extends BaseCrudApiTest {

    private Map<String, Object> body(Integer dayOfWeek, Integer period, String start, String end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dayOfWeek", dayOfWeek);
        m.put("period", period);
        m.put("startTime", start);
        m.put("endTime", end);
        return m;
    }

    private JsonNode listData() throws Exception {
        MvcResult result = getJson("/api/time-slots", adminToken()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(200, json.path("code").asInt(), "查询时间段失败: " + json);
        return json.path("data");
    }

    @Test
    @DisplayName("时间段1: 25个初始化时间段可查询, 按(星期,节次)升序且覆盖全部组合")
    void listReturns25InOrder() throws Exception {
        JsonNode data = listData();
        assertEquals(25, data.size(), "初始化时间段应为 5天×5大节=25 条, 实际: " + data.size());

        boolean[][] present = new boolean[6][6];
        int prevKey = 0;
        for (int i = 0; i < data.size(); i++) {
            JsonNode item = data.get(i);
            int d = item.path("dayOfWeek").asInt();
            int p = item.path("period").asInt();
            assertTrue(d >= 1 && d <= 5, "出现非法星期: " + item);
            assertTrue(p >= 1 && p <= 5, "出现非法节次: " + item);
            int key = d * 10 + p;
            assertTrue(key > prevKey, "时间段未按(dayOfWeek,period)升序, 第" + i + "项: " + item);
            prevKey = key;
            present[d][p] = true;
        }

        // 首项应为周一第1大节, 末项应为周五第5大节
        assertEquals(1, data.get(0).path("dayOfWeek").asInt());
        assertEquals(1, data.get(0).path("period").asInt());
        assertEquals(5, data.get(24).path("dayOfWeek").asInt());
        assertEquals(5, data.get(24).path("period").asInt());

        // 覆盖全部 25 个组合
        for (int d = 1; d <= 5; d++) {
            for (int p = 1; p <= 5; p++) {
                assertTrue(present[d][p], "缺少时间段: 星期" + d + " 第" + p + "大节");
            }
        }
    }

    @Test
    @DisplayName("时间段2: 已有时间段按id查询详情(周一第1大节)")
    void detailOfExistingSlot() throws Exception {
        JsonNode data = listData();
        long id = data.get(0).path("id").asLong();

        getJson("/api/time-slots/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dayOfWeek").value(1))
                .andExpect(jsonPath("$.data.period").value(1))
                .andExpect(jsonPath("$.data.startTime").value("08:00"))
                .andExpect(jsonPath("$.data.endTime").value("09:35"));
    }

    @Test
    @DisplayName("时间段3: 不存在的id -> code404")
    void detailNotFound() throws Exception {
        getJson("/api/time-slots/99999999", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("时间段不存在")));
    }

    @Test
    @DisplayName("时间段4: 重复(dayOfWeek,period)新增被拒绝 -> code400")
    void createDuplicateRejected() throws Exception {
        // (1,1) 已被初始化占用
        postJson("/api/time-slots", body(1, 1, "20:00", "21:35"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("该时间段已存在")));
    }

    @Test
    @DisplayName("时间段5: 非法星期(0/6)新增被拒绝 -> HTTP400")
    void invalidDayOfWeekRejected() throws Exception {
        postJson("/api/time-slots", body(0, 1, "08:00", "09:35"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("星期非法")));

        postJson("/api/time-slots", body(6, 1, "08:00", "09:35"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("星期非法")));
    }

    @Test
    @DisplayName("时间段6: 非法节次(0/6)新增被拒绝 -> HTTP400")
    void invalidPeriodRejected() throws Exception {
        postJson("/api/time-slots", body(1, 0, "08:00", "09:35"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("节次非法")));

        postJson("/api/time-slots", body(1, 6, "08:00", "09:35"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("节次非法")));
    }

    @Test
    @DisplayName("时间段7: 无Token访问写接口 -> HTTP401")
    void noTokenReturns401() throws Exception {
        postWithoutToken("/api/time-slots", body(5, 5, "20:00", "21:35"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("时间段8: TEACHER访问写接口 -> HTTP403")
    void teacherRoleForbidden() throws Exception {
        postJson("/api/time-slots", body(5, 5, "20:00", "21:35"), teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("时间段9: 已登录角色(TEACHER)可读取列表")
    void teacherCanReadList() throws Exception {
        MvcResult result = getJson("/api/time-slots", teacherToken()).andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(200, json.path("code").asInt());
        assertEquals(25, json.path("data").size());
    }
}
