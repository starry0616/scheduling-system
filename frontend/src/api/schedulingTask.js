import request from './request'

// ============ 排课任务(SchedulingTask) API ============

/** 任务列表(可选 semester/status 过滤) */
export function listSchedulingTasks(params) {
  return request.get('/scheduling-tasks', { params })
}

/** 任务详情(基本信息 + SA参数 + 软约束权重) */
export function getSchedulingTask(id) {
  return request.get(`/scheduling-tasks/${id}`)
}

/** 任务完整数据: 任务 + 纳入开课实例(含班级) + 教室池 + 时间段(详情页/配置展示) */
export function getSchedulingTaskData(id) {
  return request.get(`/scheduling-tasks/${id}/data`)
}

/** 与任务同学期的可纳入开课候选(添加课程弹窗) */
export function getTaskOfferingOptions(id) {
  return request.get(`/scheduling-tasks/${id}/offering-options`)
}

/** 新增任务(状态固定 PENDING; 数值缺省走系统默认) */
export function createSchedulingTask(data) {
  return request.post('/scheduling-tasks', data)
}

/** 修改任务(仅 PENDING 允许; 数值为 null 表示保持现值) */
export function updateSchedulingTask(id, data) {
  return request.put(`/scheduling-tasks/${id}`, data)
}

/** 删除任务(仅 PENDING 允许) */
export function deleteSchedulingTask(id) {
  return request.delete(`/scheduling-tasks/${id}`)
}

// ---- 任务-课程范围 ----

export function listTaskCourseOfferings(taskId) {
  return request.get(`/scheduling-tasks/${taskId}/course-offerings`)
}

/** 覆盖式保存课程范围(替换语义, 由后端保证事务内幂等) */
export function replaceTaskCourseOfferings(taskId, courseOfferingIds) {
  return request.put(`/scheduling-tasks/${taskId}/course-offerings`, { courseOfferingIds })
}

// ---- 任务-教室范围 ----

export function listTaskClassrooms(taskId) {
  return request.get(`/scheduling-tasks/${taskId}/classrooms`)
}

/** 覆盖式保存教室范围(替换语义) */
export function replaceTaskClassrooms(taskId, classroomIds) {
  return request.put(`/scheduling-tasks/${taskId}/classrooms`, { classroomIds })
}

// ---- 执行 ----

/** 开始排课(同步执行; ADMIN; 仅 PENDING); 单请求放宽超时到 10 分钟 */
export function runSchedulingTask(taskId) {
  return request.post(`/scheduling-tasks/${taskId}/run`, null, { timeout: 600000 })
}
