<template>
  <div class="page" v-loading="loading">
    <el-page-header :content="`排课结果 - ${task ? task.taskName : taskId}`" @back="router.back()" />

    <!-- 无结果占位 -->
    <el-empty v-if="state === 'empty'" description="该任务尚未执行，暂无排课结果">
      <el-button v-if="task && task.status === 'PENDING'" type="primary" @click="$router.push(`/admin/tasks/${taskId}`)">
        返回任务详情并开始排课
      </el-button>
      <el-button v-else type="primary" plain @click="$router.push(`/admin/tasks/${taskId}`)">返回任务详情</el-button>
    </el-empty>

    <template v-if="state === 'ready' && summary">
      <el-alert :type="summary.feasible ? 'success' : 'warning'" :closable="false" show-icon style="margin-top: 16px"
        :title="summary.feasible ? '排课结果可行：已生成满足全部硬约束的课表' : '排课结果不可行：算法已完成，但仍存在硬约束冲突'" />

      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :span="4">
          <el-card shadow="never">
            <el-statistic title="硬约束冲突" :value="summary.hardViolation ?? '-'">
              <template #suffix>
                <el-tag v-if="summary.hardViolation === 0" type="success" size="small">无</el-tag>
                <el-tag v-else type="danger" size="small">有</el-tag>
              </template>
            </el-statistic>
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <el-statistic title="软约束违反(评分)" :value="summary.softPenalty ?? 0" :precision="1" />
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <el-statistic title="Energy(适应度)" :value="summary.energy ?? 0" :precision="1" />
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <el-statistic title="运行时间(ms)" :value="summary.runtimeMs ?? '-'" />
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <el-statistic title="迭代次数" :value="summary.iterations ?? '-'" />
          </el-card>
        </el-col>
        <el-col :span="4">
          <el-card shadow="never">
            <el-statistic title="随机种子" :value="summary.seed ?? '-'" />
          </el-card>
        </el-col>
      </el-row>

      <el-descriptions :column="3" border style="margin-top: 16px">
        <el-descriptions-item label="结果状态">
          <el-tag :type="summary.feasible ? 'success' : 'warning'" effect="light">{{ summary.outcome }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="是否可行">{{ summary.feasible ? '可行(FEASIBLE)' : '部分冲突(BEST_EFFORT)' }}</el-descriptions-item>
        <el-descriptions-item label="结果ID">{{ summary.resultId }}</el-descriptions-item>
        <el-descriptions-item label="硬约束权重 W_hard">{{ summary.hardConstraintWeight }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">{{ statusMeta[summary.status]?.label || summary.status }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ fmtTime(task?.finishTime) || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-card shadow="never" style="margin-top: 16px">
        <template #header>排课结果操作</template>
        <el-button type="primary" :icon="Calendar" @click="$router.push(`/admin/tasks/${taskId}/schedule`)">查看标准周课表</el-button>
        <el-button :icon="Back" @click="$router.push(`/admin/tasks/${taskId}`)">返回任务详情</el-button>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, Back, Refresh } from '@element-plus/icons-vue'
import { getResultByTask } from '@/api/schedulingResult'
import { getSchedulingTask } from '@/api/schedulingTask'
import { TASK_STATUS } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id

const statusMeta = TASK_STATUS
const loading = ref(false)
const state = ref('loading') // loading | ready | empty
const task = ref(null)
const summary = ref(null)

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

async function loadAll() {
  loading.value = true
  state.value = 'loading'
  try {
    try {
      const detailRes = await getSchedulingTask(taskId)
      task.value = detailRes.data || null
    } catch {
      task.value = null
    }
    const res = await getResultByTask(taskId, true)
    summary.value = res.data || null
    state.value = summary.value ? 'ready' : 'empty'
  } catch (e) {
    // 404(尚未执行)视为空态; 其他错误也落入空态并保留详情导航
    summary.value = null
    state.value = 'empty'
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>
