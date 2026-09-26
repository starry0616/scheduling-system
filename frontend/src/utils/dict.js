// ============ 业务字典: 状态/类型标签与颜色 ============

export const TASK_STATUS = {
  PENDING: { label: '待排课', type: 'info' },
  RUNNING: { label: '排课中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '排课失败', type: 'danger' }
}

export const ROOM_TYPE = {
  NORMAL: { label: '普通教室', type: 'info' },
  MULTIMEDIA: { label: '多媒体教室', type: 'success' },
  LAB: { label: '实验室', type: 'warning' }
}

export const COURSE_TYPE = {
  THEORY: { label: '理论课', type: 'info' },
  LAB: { label: '实验课', type: 'warning' }
}

export const DAY_NAMES = ['', '星期一', '星期二', '星期三', '星期四', '星期五']

export const PERIOD_NAMES = ['', '第1节', '第2节', '第3节', '第4节', '第5节']

/** 状态标签 (Element Plus tag type) */
export function statusTag(status) {
  return TASK_STATUS[status] || { label: status, type: 'info' }
}

export function roomTypeLabel(type) {
  return (ROOM_TYPE[type] || { label: type }).label
}

export function courseTypeLabel(type) {
  return (COURSE_TYPE[type] || { label: type }).label
}
