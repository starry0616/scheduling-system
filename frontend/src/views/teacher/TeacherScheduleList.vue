<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>已完成排课任务(可按 班级/教师/教室 查看课表)</span>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="教师端为只读视图。可查看已完成任务的排课结果，并按班级/教师/教室维度查询标准周课表。当前后端仅提供任务全局查询，可查看本批次全部排课记录。" />
      <el-table :data="tasks" v-loading="loading" empty-text="暂无已完成的排课任务">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="semester" label="学期" width="110" />
        <el-table-column prop="weekCount" label="周数" width="80" align="center">
          <template #default="{ row }">{{ row.weekCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.finishTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :icon="Calendar" @click="goSchedule(row)">查看课表</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Calendar } from '@element-plus/icons-vue'
import { listSchedulingTasks } from '@/api/schedulingTask'

const router = useRouter()
const loading = ref(false)
const tasks = ref([])

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

async function load() {
  loading.value = true
  try {
    const res = await listSchedulingTasks({ status: 'COMPLETED' })
    tasks.value = res.data || []
  } finally {
    loading.value = false
  }
}

function goSchedule(row) {
  router.push(`/teacher/tasks/${row.id}/schedule`)
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
