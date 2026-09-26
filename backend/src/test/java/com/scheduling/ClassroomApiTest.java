package com.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教室基础管理 Web 层测试 (第3步第二阶段)
 * 覆盖: 正常请求/参数校验/查询/新增/修改/删除/重复与非法数据/不存在数据/401/403
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClassroomApiTest extends BaseCrudApiTest {

    private Map<String, Object> body(String roomNo, String building, Integer capacity, String roomType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomNo", roomNo);
        m.put("building", building);
        m.put("capacity", capacity);
        m.put("roomType", roomType);
        return m;
    }

    @Test
    @DisplayName("教室1: 新增成功后按id查询详情")
    void createAndDetail() throws Exception {
        String no = "A" + uniqueSuffix();
        long id = createAndGetId("/api/classrooms", body(no, "第一教学楼", 80, "MULTIMEDIA"), adminToken());

        getJson("/api/classrooms/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roomNo").value(no))
                .andExpect(jsonPath("$.data.building").value("第一教学楼"))
                .andExpect(jsonPath("$.data.capacity").value(80))
                .andExpect(jsonPath("$.data.roomType").value("MULTIMEDIA"));

        deleteJson("/api/classrooms/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("教室2: 参数校验失败 -> HTTP400, 编号为空/容量缺失或为0")
    void createValidationFails() throws Exception {
        // 编号为空
        postJson("/api/classrooms", body("  ", "实验楼", 60, "LAB"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("教室编号不能为空")));

        // 容量缺失 + 教室类型超长
        Map<String, Object> m = new HashMap<>();
        m.put("roomNo", "B" + uniqueSuffix());
        m.put("capacity", null);
        m.put("roomType", "X".repeat(25));
        postJson("/api/classrooms", m, adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("教室容量不能为空")));

        // 容量为0
        postJson("/api/classrooms", body("C" + uniqueSuffix(), null, 0, "NORMAL"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("教室容量必须大于等于1")));
    }

    @Test
    @DisplayName("教室3: 重复教室编号 -> code400")
    void createDuplicateRoomNoFails() throws Exception {
        String no = "DUP" + uniqueSuffix();
        long id = createAndGetId("/api/classrooms", body(no, "三号楼", 50, "NORMAL"), adminToken());

        postJson("/api/classrooms", body(no, "四号楼", 60, "LAB"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("教室编号已存在")));

        deleteJson("/api/classrooms/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("教室4: 非法教室类型 -> code400")
    void createInvalidRoomTypeFails() throws Exception {
        postJson("/api/classrooms", body("INV" + uniqueSuffix(), "某楼", 40, "GYM"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("教室类型非法")));
    }

    @Test
    @DisplayName("教室5: 列表 + 关键字模糊查询")
    void listWithKeyword() throws Exception {
        String no = "S" + uniqueSuffix();
        long id = createAndGetId("/api/classrooms", body(no, "图书馆楼", 120, "NORMAL"), adminToken());

        getJson("/api/classrooms", Map.of("keyword", no), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].roomNo").value(no));

        // 按楼栋模糊
        getJson("/api/classrooms", Map.of("keyword", "图书馆"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(id));

        deleteJson("/api/classrooms/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("教室6: 修改成功")
    void updateSuccess() throws Exception {
        String no = "UP" + uniqueSuffix();
        long id = createAndGetId("/api/classrooms", body(no, "一教", 60, "NORMAL"), adminToken());

        putJson("/api/classrooms/" + id, body(no, "一教(改造)", 90, "MULTIMEDIA"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.capacity").value(90))
                .andExpect(jsonPath("$.data.roomType").value("MULTIMEDIA"))
                .andExpect(jsonPath("$.data.building").value("一教(改造)"));

        deleteJson("/api/classrooms/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("教室7: 修改不存在的教室 -> code404")
    void updateNotFound() throws Exception {
        putJson("/api/classrooms/99999999", body("X1", "某楼", 30, "NORMAL"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("教室不存在")));
    }

    @Test
    @DisplayName("教室8: 删除成功后查询 -> code404")
    void deleteSuccessThenNotFound() throws Exception {
        String no = "DEL" + uniqueSuffix();
        long id = createAndGetId("/api/classrooms", body(no, "某楼", 45, "LAB"), adminToken());

        deleteJson("/api/classrooms/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        getJson("/api/classrooms/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("教室9: 无Token访问写接口 -> HTTP401")
    void noTokenReturns401() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("roomNo", "N" + uniqueSuffix());
        m.put("capacity", 30);
        postWithoutToken("/api/classrooms", m).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("教室10: TEACHER访问写接口 -> HTTP403")
    void teacherRoleForbidden() throws Exception {
        postJson("/api/classrooms", body("T" + uniqueSuffix(), "某楼", 30, "NORMAL"), teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
