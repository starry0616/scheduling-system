# 基于模拟退火算法的自动排课系统

一个完整的教务自动排课 Web 系统：使用**模拟退火算法**求解排课问题（硬约束可行性 + 软约束质量优化），并提供课程/班级/教师/教室/时间段管理与排课任务执行、结果查询的一站式界面。

> 面向毕业设计/课程设计场景开发，内置**稳定可重复的演示数据**与**算法参数实验模块**，开箱即跑。

## ✨ 功能特性

- **三角色权限**：管理员（基础数据管理 + 发起排课）、教师（查看个人课表）、学生（查看班级课表）
- **完整基础数据管理**：课程、班级、教师、教室（理论/多媒体/LAB 三种类型）、时间段、开课实例（支持合班、教师偏好、资源不可用约束）
- **模拟退火排课引擎**：贪心/随机初始解、自适应初始温度、Metropolis 准则、指数降温、三种邻域算子（MOVE / SWAP / CHANGE_ROOM）、硬约束优先的冲突修复、随机种子可复现
- **算法参数实验**：冷却率、初始策略、每温度邻居数四组对照实验（共 190 次运行），自动输出 CSV + 统计报告，用于论文/答辩数据支撑
- **演示数据**：启动时自动幂等初始化一套完整演示数据（含可直接执行的排课任务），一键演示
- **测试覆盖**：177 个单元/集成测试全部通过（算法、鉴权、CRUD API、端到端流程）

## 🛠️ 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3 · Spring Data JPA · MySQL 8 · JWT 鉴权 · Maven |
| 前端 | Vue 3 · Vite · Element Plus · Pinia · Vue Router · ECharts · Axios |
| 算法 | 模拟退火（Simulated Annealing），纯 Java 实现，无第三方依赖 |

## 📁 项目结构

```
scheduling-system/
├── backend/                      # Spring Boot 后端
│   ├── src/main/java/com/scheduling/
│   │   ├── algorithm/            # 模拟退火核心算法（19 个类）
│   │   │   └── experiment/       # 参数实验模块（13 个类）
│   │   ├── controller/           # REST API
│   │   ├── service/              # 业务逻辑（含演示数据初始化）
│   │   ├── repository/           # 数据访问
│   │   ├── entity/ dto/ vo/      # 实体与传输对象
│   │   ├── config/               # JWT 鉴权、角色拦截、CORS
│   │   └── execution/            # 排课任务执行服务
│   ├── src/test/                 # 177 个测试（算法 + API + 集成）
│   └── experiment-output/        # 实验数据（CSV + 统计报告）
├── frontend/                     # Vue 3 前端
│   └── src/views/                # 登录/管理员/教师/学生页面
└── scripts/                      # 启动脚本（后端/前端）
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- MySQL 8（服务已启动，默认端口 3306）
- Node.js 18+

### 1. 配置数据库

默认连接 `localhost:3306/scheduling_system`（库不存在会自动创建），账号 `root` / 密码 `123456`：

```yaml
# backend/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/scheduling_system?createDatabaseIfNotExist=true&...
    username: root
    password: 123456      # ← 改成你自己的 MySQL 密码
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

启动时自动建表并幂等初始化演示数据（`app.demo-data.enabled: true`），后端运行于 **http://localhost:8080**（接口前缀 `/api`）。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 **http://localhost:5173**。

> 也可直接运行 `scripts/phase7-start-backend.cmd` 与 `scripts/start-frontend.cmd`。

### 默认演示账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 教师 | `demo_teacher01` ~ `demo_teacher06` | `teacher123` |

## 🧠 算法与实验结论

排课问题建模为带硬约束（教师/班级/教室/时间段不冲突）与软约束（教师偏好、时间段合理性等）的能量最小化问题，能量越低方案越优。

实验结论（数据见 `backend/experiment-output/`，全部 100% 可行解）：

- **冷却率**：`coolingRate=0.995` 时解质量最优（能量 2281，均值），`0.85` 时最差（4680）——降温越慢搜索越充分
- **每温度邻居数**：`neighborsPerTemp=80` 优于 5/10/20/40（能量 2539 vs 4947），但耗时线性增加
- **初始策略**：GREEDY 在小/中规模更优，RANDOM 在大规模数据集略优（能量 9675 vs 9759）

## ✅ 测试

```bash
cd backend
mvn test
```

23 个测试类、**177 个测试全部通过**：`SimulatedAnnealingTest`、`FitnessConstraintsTest`、`InitialStrategyTest`、`CandidateBuilderTest`、`ExperimentMatrixTest`，以及鉴权（JWT/角色）、CRUD API、排课执行、演示数据等集成测试。

## 📄 协议

本项目仅供学习交流使用。
