<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="never" @click="goTasks" class="clickable">
          <template #header>全部排课任务</template>
          <div class="stat-number">{{ stats.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" @click="goTasks" class="clickable">
          <template #header>已完成</template>
          <div class="stat-number success">{{ stats.COMPLETED }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" @click="goTasks" class="clickable">
          <template #header>待排课</template>
          <div class="stat-number pending">{{ stats.PENDING }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" @click="goTasks" class="clickable">
          <template #header>排课失败</template>
          <div class="stat-number danger">{{ stats.FAILED }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>最近排课任务</span>
          <div>
            <el-button link type="primary" @click="router.push('/admin/tasks')">查看全部任务</el-button>
            <el-button type="primary" size="small" @click="router.push('/admin/tasks')">新建任务</el-button>
          </div>
        </div>
      </template>
      <el-table :data="recent" v-loading="loading" size="small" empty-text="暂无排课任务">
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="semester" label="学期" width="110" />
        <el-table-column prop="weekCount" label="周数" width="80" align="center">
          <template #default="{ row }">{{ row.weekCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta[row.status]?.type || 'info'" effect="light">{{ statusMeta[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/admin/tasks/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 20px">
      <template #header>系统说明</template>
      <p>基于模拟退火算法的自动排课系统 - 管理员后台(阶段七: 排课任务前端闭环)。</p>
      <p>流程: 新建任务 → 配置课程/教室范围 → 开始排课 → 查看结果 → 标准周课表(班级/教师/教室维度)。</p>
      <p>当前用户: {{ authStore.realName }} ({{ authStore.role }})</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { listSchedulingTasks } from '@/api/schedulingTask'
import { TASK_STATUS } from '@/utils/dict'

const router = useRouter()
const authStore = useAuthStore()
const statusMeta = TASK_STATUS

const loading = ref(false)
const stats = reactive({ total: 0, PENDING: 0, RUNNING: 0, COMPLETED: 0, FAILED: 0 })
const recent = ref([])

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

function goTasks() {
  router.push('/admin/tasks')
}

async function load() {
  loading.value = true
  try {
    const res = await listSchedulingTasks()
    const rows = res.data || []
    stats.total = rows.length
    stats.PENDING = stats.RUNNING = stats.COMPLETED = stats.FAILED = 0
    rows.forEach((r) => {
      if (stats[r.status] !== undefined) stats[r.status]++
    })
    recent.value = rows.slice(0, 8)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.stat-number {
  font-size: 26px;
  font-weight: 500;
  color: #409eff;
  text-align: center;
}
.stat-number.success {
  color: #67c23a;
}
.stat-number.pending {
  color: #909399;
}
.stat-number.danger {
  color: #f56c6c;
}
.clickable {
  cursor: pointer;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
