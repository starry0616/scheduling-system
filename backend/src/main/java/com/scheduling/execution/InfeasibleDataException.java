package com.scheduling.execution;

import com.scheduling.exception.BusinessException;

/**
 * 排课数据不可行异常(阶段六)。
 *
 * 触发场景(在进入模拟退火之前必须检出):
 * <pre>
 *   1. 任务未纳入任何开课实例 / 未配置教室池 / 无时间段;
 *   2. 某 SchedulingUnit 无可选候选(candidates.isEmpty(), 如教室容量/类型不足、
 *      教师/班级/教室不可用时间覆盖全部可用时段等);
 *   3. weeklySessions &gt; 每周可用工作日数(5 天模型下 &gt; 5 即不可行);
 *   4. offering 引用损坏(缺课程元数据/无授课班级等)。
 * </pre>
 * 抛出后由 SchedulingExecutionService 先将任务置 FAILED(短事务),
 * 再向调用方返回携带不可行原因的 400 业务错误。
 */
public class InfeasibleDataException extends BusinessException {

    public InfeasibleDataException(String reason) {
        super(400, "INFEASIBLE_DATA: " + reason);
    }
}
