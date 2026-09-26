import request from './request'

// ============ 排课结果(SchedulingResult) 只读查询 ============

/** 按任务查询结果统计摘要(任务未执行时后端返回 404; silent=true 时不弹错误提示) */
export function getResultByTask(taskId, silent = true) {
  return request.get(`/scheduling-results/task/${taskId}`, { silentError: silent })
}

/** 按结果ID查询完整课表条目(含课程/教师/教室/班级/时间); silent=true 时不弹错误提示 */
export function getScheduleByResult(resultId, silent = true) {
  return request.get(`/scheduling-results/${resultId}/schedule`, { silentError: silent })
}
