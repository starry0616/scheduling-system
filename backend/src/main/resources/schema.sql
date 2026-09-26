-- ============================================================
-- 基于模拟退火算法的自动排课系统 - 数据库建表脚本 V2.2
-- MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS scheduling_system
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE scheduling_system;

-- ============================================================
-- 1. sys_user 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL UNIQUE              COMMENT '用户名',
  password    VARCHAR(100) NOT NULL                    COMMENT '密码(BCrypt加密)',
  real_name   VARCHAR(50)  NOT NULL                     COMMENT '真实姓名',
  role        VARCHAR(20)  NOT NULL                    COMMENT '角色: ADMIN/TEACHER/STUDENT',
  phone       VARCHAR(20)                               COMMENT '手机号',
  status      TINYINT      DEFAULT 1                    COMMENT '状态: 1启用 0禁用',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='用户表';

-- ============================================================
-- 2. teacher 教师表
-- ============================================================
CREATE TABLE IF NOT EXISTS teacher (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL UNIQUE              COMMENT '关联用户ID',
  teacher_no  VARCHAR(20)  NOT NULL UNIQUE              COMMENT '教师工号',
  name        VARCHAR(50)  NOT NULL                     COMMENT '教师姓名',
  title       VARCHAR(50)                               COMMENT '职称: 教授/副教授/讲师',
  department  VARCHAR(100)                              COMMENT '所属院系',
  CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='教师表';

-- ============================================================
-- 3. student 学生表
-- ============================================================
CREATE TABLE IF NOT EXISTS student (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL UNIQUE              COMMENT '关联用户ID',
  student_no  VARCHAR(20)  NOT NULL UNIQUE              COMMENT '学号',
  name        VARCHAR(50)  NOT NULL                     COMMENT '学生姓名',
  class_id    BIGINT                                    COMMENT '班级ID',
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB COMMENT='学生表';

-- ============================================================
-- 4. class 班级表
-- ============================================================
CREATE TABLE IF NOT EXISTS class (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  class_name    VARCHAR(50)  NOT NULL UNIQUE            COMMENT '班级名称',
  grade         VARCHAR(10)                             COMMENT '年级: 2023级',
  student_count INT          NOT NULL                   COMMENT '班级人数',
  department    VARCHAR(100)                            COMMENT '所属院系'
) ENGINE=InnoDB COMMENT='班级表';

-- 班级外键 (student.class_id → class.id)
ALTER TABLE student
  ADD CONSTRAINT fk_student_class FOREIGN KEY (class_id) REFERENCES class(id);

-- ============================================================
-- 5. course 课程表
-- ============================================================
CREATE TABLE IF NOT EXISTS course (
  id                   BIGINT       PRIMARY KEY AUTO_INCREMENT,
  course_code          VARCHAR(20)  NOT NULL UNIQUE     COMMENT '课程代码',
  course_name          VARCHAR(100) NOT NULL             COMMENT '课程名称',
  course_type          VARCHAR(20)  DEFAULT 'THEORY'    COMMENT '课程类型: THEORY/LAB',
  required_room_type   VARCHAR(20)  DEFAULT 'NORMAL'    COMMENT '要求教室类型: NORMAL/MULTIMEDIA/LAB'
) ENGINE=InnoDB COMMENT='课程表';

-- ============================================================
-- 6. course_offering 开课实例表 (V2.2: weekly_sessions + duration_slots)
-- ============================================================
CREATE TABLE IF NOT EXISTS course_offering (
  id               BIGINT       PRIMARY KEY AUTO_INCREMENT,
  course_id        BIGINT       NOT NULL               COMMENT '课程ID',
  teacher_id       BIGINT       NOT NULL               COMMENT '授课教师ID',
  semester         VARCHAR(20)  NOT NULL               COMMENT '学期: 2026春',
  weekly_sessions  INT          NOT NULL                COMMENT '每周上课次数(每次1大节=2学时)',
  duration_slots   INT          NOT NULL DEFAULT 1     COMMENT '每次课连续占用的大节数(理论课=1, 实验课=2)',
  is_lab_course    TINYINT      DEFAULT 0              COMMENT '是否实验课(=duration_slots>1)',
  CONSTRAINT fk_offering_course  FOREIGN KEY (course_id)  REFERENCES course(id),
  CONSTRAINT fk_offering_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id)
) ENGINE=InnoDB COMMENT='开课实例表';

-- ============================================================
-- 7. course_offering_class 开课实例-班级关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS course_offering_class (
  id                  BIGINT  PRIMARY KEY AUTO_INCREMENT,
  course_offering_id  BIGINT  NOT NULL                  COMMENT '开课实例ID',
  class_id            BIGINT  NOT NULL                  COMMENT '班级ID',
  UNIQUE KEY uk_offering_class (course_offering_id, class_id),
  CONSTRAINT fk_coc_offering FOREIGN KEY (course_offering_id) REFERENCES course_offering(id) ON DELETE CASCADE,
  CONSTRAINT fk_coc_class    FOREIGN KEY (class_id)           REFERENCES class(id)
) ENGINE=InnoDB COMMENT='开课实例-班级关联表';

-- ============================================================
-- 8. classroom 教室表
-- ============================================================
CREATE TABLE IF NOT EXISTS classroom (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  room_no    VARCHAR(20)  NOT NULL UNIQUE               COMMENT '教室编号',
  building   VARCHAR(50)                                COMMENT '楼栋',
  capacity   INT          NOT NULL                      COMMENT '教室容量',
  room_type  VARCHAR(20)  NOT NULL                      COMMENT '教室类型: NORMAL/MULTIMEDIA/LAB'
) ENGINE=InnoDB COMMENT='教室表';

-- ============================================================
-- 9. time_slot 时间段表
-- ============================================================
CREATE TABLE IF NOT EXISTS time_slot (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  day_of_week TINYINT      NOT NULL                     COMMENT '星期几: 1=周一 ... 5=周五',
  period      TINYINT      NOT NULL                     COMMENT '大节序号: 1=第1-2节, 2=第3-4节, 3=第5-6节, 4=第7-8节, 5=第9-10节',
  start_time  VARCHAR(10)                               COMMENT '开始时间',
  end_time    VARCHAR(10)                               COMMENT '结束时间',
  UNIQUE KEY uk_day_period (day_of_week, period)
) ENGINE=InnoDB COMMENT='时间段表';

-- ============================================================
-- 10. teacher_preference 教师时间偏好表
-- ============================================================
CREATE TABLE IF NOT EXISTS teacher_preference (
  id               BIGINT  PRIMARY KEY AUTO_INCREMENT,
  teacher_id       BIGINT  NOT NULL                     COMMENT '教师ID',
  time_slot_id     BIGINT  NOT NULL                     COMMENT '时间段ID',
  preference_level TINYINT DEFAULT 0                    COMMENT '偏好级别: 1=偏好, -1=不偏好, 0=一般',
  UNIQUE KEY uk_teacher_slot (teacher_id, time_slot_id),
  CONSTRAINT fk_pref_teacher FOREIGN KEY (teacher_id)   REFERENCES teacher(id) ON DELETE CASCADE,
  CONSTRAINT fk_pref_slot    FOREIGN KEY (time_slot_id) REFERENCES time_slot(id)
) ENGINE=InnoDB COMMENT='教师时间偏好表';

-- ============================================================
-- 11. resource_unavailability 资源不可用时间表 (V2.2新增)
-- ============================================================
CREATE TABLE IF NOT EXISTS resource_unavailability (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  resource_type VARCHAR(20)  NOT NULL                   COMMENT '资源类型: TEACHER/CLASS/CLASSROOM',
  resource_id   BIGINT       NOT NULL                  COMMENT '资源ID(teacher.id/class.id/classroom.id)',
  time_slot_id  BIGINT       NOT NULL                  COMMENT '不可用时间段ID',
  reason        VARCHAR(200)                           COMMENT '不可用原因',
  UNIQUE KEY uk_resource_slot (resource_type, resource_id, time_slot_id),
  CONSTRAINT fk_unavail_slot FOREIGN KEY (time_slot_id) REFERENCES time_slot(id)
) ENGINE=InnoDB COMMENT='资源不可用时间表';

-- ============================================================
-- 12. scheduling_task 排课任务表
-- ============================================================
CREATE TABLE IF NOT EXISTS scheduling_task (
  id                      BIGINT       PRIMARY KEY AUTO_INCREMENT,
  task_name               VARCHAR(100) NOT NULL          COMMENT '任务名称',
  semester                VARCHAR(20)  NOT NULL          COMMENT '学期',
  week_count              INT          DEFAULT 16       COMMENT '总周数',
  status                  VARCHAR(20)  DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED',
  -- SA算法参数
  max_initial_temp        DOUBLE       DEFAULT 1000.0   COMMENT '初始温度上限',
  min_initial_temp        DOUBLE       DEFAULT 10.0     COMMENT '初始温度下限',
  min_temp                DOUBLE       DEFAULT 0.1      COMMENT '终止温度',
  cooling_rate            DOUBLE       DEFAULT 0.95     COMMENT '降温系数',
  max_temp_iterations     INT          DEFAULT 5000     COMMENT '温度迭代次数(外循环)',
  neighbors_per_temp      INT          DEFAULT 20       COMMENT '每温度邻域评价次数(内循环)',
  max_repair_attempts     INT          DEFAULT 3        COMMENT '冲突修复最大轮数',
  random_seed             BIGINT                          COMMENT '随机种子(null=系统时间)',
  -- 权重
  hard_constraint_weight  BIGINT                          COMMENT '硬约束权重(自动计算, null=按SoftMax+1)',
  w_teacher_preference    INT          DEFAULT 50       COMMENT 'S1权重',
  w_course_distribution   INT          DEFAULT 30       COMMENT 'S2权重',
  w_student_balance       INT          DEFAULT 25       COMMENT 'S3权重',
  w_teacher_continuous    INT          DEFAULT 30       COMMENT 'S4权重',
  w_student_idle          INT          DEFAULT 25       COMMENT 'S5权重',
  w_morning_evening       INT          DEFAULT 15       COMMENT 'S6权重',
  create_time             DATETIME     DEFAULT CURRENT_TIMESTAMP,
  finish_time             DATETIME
) ENGINE=InnoDB COMMENT='排课任务表';

-- ============================================================
-- 13. scheduling_task_course 排课任务-课程关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS scheduling_task_course (
  id                   BIGINT  PRIMARY KEY AUTO_INCREMENT,
  task_id              BIGINT  NOT NULL                  COMMENT '排课任务ID',
  course_offering_id   BIGINT  NOT NULL                  COMMENT '开课实例ID',
  UNIQUE KEY uk_task_offering (task_id, course_offering_id),
  CONSTRAINT fk_stc_task     FOREIGN KEY (task_id)            REFERENCES scheduling_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_stc_offering FOREIGN KEY (course_offering_id)  REFERENCES course_offering(id)
) ENGINE=InnoDB COMMENT='排课任务-课程关联表';

-- ============================================================
-- 14. scheduling_task_classroom 排课任务-教室关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS scheduling_task_classroom (
  id          BIGINT  PRIMARY KEY AUTO_INCREMENT,
  task_id     BIGINT  NOT NULL                         COMMENT '排课任务ID',
  classroom_id BIGINT NOT NULL                         COMMENT '教室ID',
  UNIQUE KEY uk_task_classroom (task_id, classroom_id),
  CONSTRAINT fk_stc2_task      FOREIGN KEY (task_id)     REFERENCES scheduling_task(id) ON DELETE CASCADE,
  CONSTRAINT fk_stc2_classroom FOREIGN KEY (classroom_id) REFERENCES classroom(id)
) ENGINE=InnoDB COMMENT='排课任务-教室关联表';

-- ============================================================
-- 15. scheduling_result 排课结果统计表
-- ============================================================
CREATE TABLE IF NOT EXISTS scheduling_result (
  id                    BIGINT   PRIMARY KEY AUTO_INCREMENT,
  task_id               BIGINT   NOT NULL UNIQUE        COMMENT '排课任务ID',
  best_fitness          DOUBLE   NOT NULL               COMMENT '最终能量值',
  hard_violation_count  INT      NOT NULL               COMMENT '硬约束违反总数',
  soft_violation_count  INT      NOT NULL               COMMENT '软约束违反总数',
  iteration_count       INT      NOT NULL               COMMENT '实际温度迭代次数',
  total_neighbor_evals   BIGINT  NOT NULL                COMMENT '总邻域评价次数',
  execution_time_ms     BIGINT   NOT NULL               COMMENT '运行时间(毫秒)',
  initial_fitness       DOUBLE                          COMMENT '初始解能量',
  iteration_history     TEXT                            COMMENT 'JSON: [{tempIteration, temperature, currentEnergy, bestEnergy, hardViolation, softPenalty}]',
  finish_time           DATETIME DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_result_task FOREIGN KEY (task_id) REFERENCES scheduling_task(id)
) ENGINE=InnoDB COMMENT='排课结果统计表';

-- ============================================================
-- 16. schedule_entry 排课结果条目表
-- ============================================================
CREATE TABLE IF NOT EXISTS schedule_entry (
  id                  BIGINT   PRIMARY KEY AUTO_INCREMENT,
  task_id             BIGINT   NOT NULL                 COMMENT '排课任务ID',
  course_offering_id  BIGINT   NOT NULL                 COMMENT '开课实例ID',
  teacher_id          BIGINT   NOT NULL                 COMMENT '教师ID',
  classroom_id        BIGINT   NOT NULL                 COMMENT '教室ID',
  time_slot_id        BIGINT   NOT NULL                 COMMENT '起始时间段ID',
  unit_index          INT      NOT NULL                 COMMENT '课次序号(第几次课)',
  CONSTRAINT fk_entry_task     FOREIGN KEY (task_id)            REFERENCES scheduling_task(id),
  CONSTRAINT fk_entry_offering FOREIGN KEY (course_offering_id) REFERENCES course_offering(id),
  CONSTRAINT fk_entry_teacher  FOREIGN KEY (teacher_id)         REFERENCES teacher(id),
  CONSTRAINT fk_entry_classroom FOREIGN KEY (classroom_id)      REFERENCES classroom(id),
  CONSTRAINT fk_entry_slot     FOREIGN KEY (time_slot_id)       REFERENCES time_slot(id)
) ENGINE=InnoDB COMMENT='排课结果条目表';

-- ============================================================
-- 17. schedule_entry_class 排课条目-班级关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS schedule_entry_class (
  id               BIGINT  PRIMARY KEY AUTO_INCREMENT,
  schedule_entry_id BIGINT NOT NULL                     COMMENT '排课条目ID',
  class_id         BIGINT  NOT NULL                     COMMENT '班级ID',
  UNIQUE KEY uk_entry_class (schedule_entry_id, class_id),
  CONSTRAINT fk_sec_entry FOREIGN KEY (schedule_entry_id) REFERENCES schedule_entry(id) ON DELETE CASCADE,
  CONSTRAINT fk_sec_class  FOREIGN KEY (class_id)         REFERENCES class(id)
) ENGINE=InnoDB COMMENT='排课条目-班级关联表';

-- ============================================================
-- 初始化数据: 时间段 (5天 × 5大节 = 25个时间段)
-- ============================================================
INSERT INTO time_slot (day_of_week, period, start_time, end_time) VALUES
-- 周一
(1, 1, '08:00', '09:35'),
(1, 2, '10:00', '11:35'),
(1, 3, '14:00', '15:35'),
(1, 4, '16:00', '17:35'),
(1, 5, '19:00', '20:35'),
-- 周二
(2, 1, '08:00', '09:35'),
(2, 2, '10:00', '11:35'),
(2, 3, '14:00', '15:35'),
(2, 4, '16:00', '17:35'),
(2, 5, '19:00', '20:35'),
-- 周三
(3, 1, '08:00', '09:35'),
(3, 2, '10:00', '11:35'),
(3, 3, '14:00', '15:35'),
(3, 4, '16:00', '17:35'),
(3, 5, '19:00', '20:35'),
-- 周四
(4, 1, '08:00', '09:35'),
(4, 2, '10:00', '11:35'),
(4, 3, '14:00', '15:35'),
(4, 4, '16:00', '17:35'),
(4, 5, '19:00', '20:35'),
-- 周五
(5, 1, '08:00', '09:35'),
(5, 2, '10:00', '11:35'),
(5, 3, '14:00', '15:35'),
(5, 4, '16:00', '17:35'),
(5, 5, '19:00', '20:35')
ON DUPLICATE KEY UPDATE start_time=VALUES(start_time), end_time=VALUES(end_time);

-- ============================================================
-- 初始化数据: 管理员账号 (用户名: admin, 密码: admin123)
-- 密码用BCrypt加密, 开发阶段先用明文, 第二阶段接入JWT时再加密
-- ============================================================
INSERT INTO sys_user (username, password, real_name, role, status) VALUES
('admin', '$2a$10$N.ZOn9G6/YLFixkdFvX0D.S6vEDQvXaFY1gkpr1cK8sF3lZl9JwJe', '系统管理员', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE password=VALUES(password);
