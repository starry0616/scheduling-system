package com.scheduling.common;

/**
 * 系统常量
 */
public class Constants {

    /** 用户角色 */
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_STUDENT = "STUDENT";

    /** 排课任务状态 */
    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_RUNNING = "RUNNING";
    public static final String TASK_COMPLETED = "COMPLETED";
    public static final String TASK_FAILED = "FAILED";

    /** 教室类型 */
    public static final String ROOM_NORMAL = "NORMAL";
    public static final String ROOM_MULTIMEDIA = "MULTIMEDIA";
    public static final String ROOM_LAB = "LAB";

    /** 课程类型 */
    public static final String COURSE_THEORY = "THEORY";
    public static final String COURSE_LAB = "LAB";

    /** 资源不可用类型 */
    public static final String RESOURCE_TEACHER = "TEACHER";
    public static final String RESOURCE_CLASS = "CLASS";
    public static final String RESOURCE_CLASSROOM = "CLASSROOM";

    /** 默认SA参数 */
    public static final double DEFAULT_MAX_INITIAL_TEMP = 1000.0;
    public static final double DEFAULT_MIN_INITIAL_TEMP = 10.0;
    public static final double DEFAULT_MIN_TEMP = 0.1;
    public static final double DEFAULT_COOLING_RATE = 0.95;
    public static final int DEFAULT_MAX_TEMP_ITERATIONS = 5000;
    public static final int DEFAULT_NEIGHBORS_PER_TEMP = 20;
    public static final int DEFAULT_MAX_REPAIR_ATTEMPTS = 3;
}
