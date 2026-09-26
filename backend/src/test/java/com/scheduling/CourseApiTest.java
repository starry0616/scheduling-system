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
 * 课程基础管理 Web 层测试
 * 覆盖: 正常请求/参数校验/查询/新增/修改/删除/不存在数据异常/角色权限
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseApiTest extends BaseCrudApiTest {

    private Map<String, Object> body(String courseCode, String courseName, String courseType, String roomType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseCode", courseCode);
        m.put("courseName", courseName);
        m.put("courseType", courseType);
        m.put("requiredRoomType", roomType);
        return m;
    }

    @Test
    @DisplayName("课程1: 新增成功后按id查询详情")
    void createAndDetail() throws Exception {
        String code = "CS" + uniqueSuffix();
        long id = createAndGetId("/api/courses", body(code, "数据结构", "THEORY", "MULTIMEDIA"), adminToken());

        getJson("/api/courses/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseCode").value(code))
                .andExpect(jsonPath("$.data.courseName").value("数据结构"))
                .andExpect(jsonPath("$.data.courseType").value("THEORY"));

        deleteJson("/api/courses/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("课程2: 参数校验失败 -> HTTP400, 课程代码为空")
    void createValidationFails() throws Exception {
        postJson("/api/courses", body(" ", "缺代码课程", "THEORY", "NORMAL"), adminToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("课程代码不能为空")));
    }

    @Test
    @DisplayName("课程3: 重复课程代码 -> code400")
    void createDuplicateCodeFails() throws Exception {
        String code = "DUP" + uniqueSuffix();
        long id = createAndGetId("/api/courses", body(code, "离散数学", "THEORY", "NORMAL"), adminToken());

        postJson("/api/courses", body(code, "同名代码另一门课", "THEORY", "NORMAL"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("课程代码已存在")));

        deleteJson("/api/courses/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("课程4: 列表 + 关键字模糊查询")
    void listWithKeyword() throws Exception {
        String code = "SE" + uniqueSuffix();
        String name = "软件工程" + uniqueSuffix();
        long id = createAndGetId("/api/courses", body(code, name, "THEORY", "NORMAL"), adminToken());

        // 列表
        getJson("/api/courses", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        // 关键字(按课程代码匹配)
        getJson("/api/courses", Map.of("keyword", code), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].courseCode").value(code))
                .andExpect(jsonPath("$.data[0].courseName").value(name));

        // 关键字(按课程名称匹配)
        getJson("/api/courses", Map.of("keyword", name), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(id));

        deleteJson("/api/courses/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("课程5: 修改成功")
    void updateSuccess() throws Exception {
        String code = "UP" + uniqueSuffix();
        long id = createAndGetId("/api/courses", body(code, "编译原理", "THEORY", "LAB"), adminToken());

        putJson("/api/courses/" + id, body(code, "编译原理(双语)", "THEORY", "NORMAL"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.courseName").value("编译原理(双语)"))
                .andExpect(jsonPath("$.data.requiredRoomType").value("NORMAL"));

        deleteJson("/api/courses/" + id, adminToken()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("课程6: 修改不存在的课程 -> code404")
    void updateNotFound() throws Exception {
        putJson("/api/courses/99999999", body("XX1", "不存在课程", "THEORY", "NORMAL"), adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("课程不存在")));
    }

    @Test
    @DisplayName("课程7: 删除成功后再查询 -> code404")
    void deleteSuccessThenNotFound() throws Exception {
        String code = "DEL" + uniqueSuffix();
        long id = createAndGetId("/api/courses", body(code, "待删除课程", "LAB", "LAB"), adminToken());

        deleteJson("/api/courses/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        getJson("/api/courses/" + id, adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("课程8: 查询不存在的课程 -> code404")
    void detailNotFound() throws Exception {
        getJson("/api/courses/99999999", adminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("课程不存在")));
    }

    @Test
    @DisplayName("课程9: 无Token访问写接口 -> HTTP401")
    void noTokenReturns401() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("courseCode", "N" + uniqueSuffix());
        m.put("courseName", "无Token课程");
        postWithoutToken("/api/courses", m).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("课程10: TEACHER访问写接口 -> HTTP403")
    void teacherRoleForbidden() throws Exception {
        postJson("/api/courses", body("T" + uniqueSuffix(), "教师越权课程", "THEORY", "NORMAL"), teacherToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
