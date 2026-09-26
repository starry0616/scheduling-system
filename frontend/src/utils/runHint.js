// 排课执行结果提示文案工具

export const OUTCOME_TEXT = {
  FEASIBLE: '排课成功，已生成可行课表',
  BEST_EFFORT: '算法已完成，但仍存在部分硬约束冲突，请检查结果'
}

/** 根据 run/summary 数据返回用户提示(如未知结果返回 null) */
export function outcomeHint(result) {
  if (!result) return null
  if (result.outcome === 'FEASIBLE' || result.feasible) return OUTCOME_TEXT.FEASIBLE
  if (result.outcome === 'BEST_EFFORT') return OUTCOME_TEXT.BEST_EFFORT
  return null
}
