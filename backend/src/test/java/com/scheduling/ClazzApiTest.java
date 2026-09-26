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
 * 班级基础管理 Web 层测试
 * 覆盖: 正常请求/参数校验/查询/新增/修改/删除/不存在数据异常/角色权限
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClazzApiTest extends BaseCrudApiTest {

    private Map<String, Object> body(String className, String grade, Integer studentCount, String department) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("className", className);
        m.put("grade", grade);
        m.put("studentCount", studentCount);
        m.put("department", department);
        return m;
    }

    @Test
    @DisplayName("班级1: 新增成功后按id查询详情")
    void createAndDetail() throws Exception {
        String name = "计科" + uniqueSuffix();
        long id = createAndGetId("/api/classes", body(name, "2023级", 42, "计算机学院"), adminToken());

        getJson("/api/classes/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.className").value(name))
                .andExpect(jsonPath("$.data.studentCount").value(42));

        deleteJson("/api/classes/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("班级2: 参数校验失败 -> HTTP400, 班级名称为空/人数为负")
    void createValidationFails() throws Exception {
        // 班级名称为空
        postJson("/api/classes", body("  ", "2023级", 30, null), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("班级名称不能为空")));

        // 人数为负
        postJson("/api/classes", body("软工" + uniqueSuffix(), "2023级", -1, null), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("班级人数不能小于0")));
    }

    @Test
    @DisplayName("班级3: 重复班级名称 -> code400")
    void createDuplicateNameFails() throws Exception {
        String name = "重复班" + uniqueSuffix();
        long id = createAndGetId("/api/classes", body(name, "2022级", 35, null), adminToken());

        postJson("/api/classes", body(name, "2023级", 40, null), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("班级名称已存在")));

        deleteJson("/api/classes/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("班级4: 列表 + 关键字模糊查询")
    void listWithKeyword() throws Exception {
        String name = "网安" + uniqueSuffix();
        long id = createAndGetId("/api/classes", body(name, "2024级", 38, "网络空间安全学院"), adminToken());

        getJson("/api/classes", Map.of("keyword", name), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].className").value(name));

        // 按年级模糊
        getJson("/api/classes", Map.of("keyword", "2024级"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(id));

        deleteJson("/api/classes/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("班级5: 修改成功")
    void updateSuccess() throws Exception {
        String name = "人工" + uniqueSuffix();
        long id = createAndGetId("/api/classes", body(name, "2022级", 30, "计算机学院"), adminToken());

        putJson("/api/classes/" + id, body(name, "2023级", 45, "人工智能学院"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentCount").value(45))
                .andExpect(jsonPath("$.data.grade").value("2023级"));

        deleteJson("/api/classes/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("班级6: 修改不存在的班级 -> code404")
    void updateNotFound() throws Exception {
        putJson("/api/classes/99999999", body("不存在班", "2023级", 30, null), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("班级不存在")));
    }

    @Test
    @DisplayName("班级7: 删除成功后查询 -> code404")
    void deleteSuccessThenNotFound() throws Exception {
        String name = "临班" + uniqueSuffix();
        long id = createAndGetId("/api/classes", body(name, "2021级", 50, null), adminToken());

        deleteJson("/api/classes/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        getJson("/api/classes/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("班级8: 查询不存在的班级 -> code404")
    void detailNotFound() throws Exception {
        getJson("/api/classes/99999999", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("班级不存在")));
    }

    @Test
    @DisplayName("班级9: 无Token访问写接口 -> HTTP401")
    void noTokenReturns401() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("className", "无Token班" + uniqueSuffix());
        m.put("studentCount", 30);
        postWithoutToken("/api/classes", m).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("班级10: TEACHER访问写接口 -> HTTP403")
    void teacherRoleForbidden() throws Exception {
        postJson("/api/classes", body("越权班" + uniqueSuffix(), "2023级", 30, null), teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
