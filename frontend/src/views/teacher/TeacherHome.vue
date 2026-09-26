<template>
  <div>
    <el-card shadow="never">
      <template #header>教师首页</template>
      <p>欢迎 {{ authStore.realName }} 老师登录自动排课系统(教师端)。</p>
      <p>可在左侧「课表查看」中浏览已完成排课任务, 并按 班级/教师/教室 维度查询标准周课表。</p>
      <p style="color: #909399; font-size: 13px">已完成的排课任务: {{ completedCount }} 个</p>
      <el-button type="primary" plain @click="router.push('/teacher/tasks')">前往课表查看</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { listSchedulingTasks } from '@/api/schedulingTask'

const router = useRouter()
const authStore = useAuthStore()
const completedCount = ref(0)

onMounted(async () => {
  try {
    const res = await listSchedulingTasks({ status: 'COMPLETED' })
    completedCount.value = (res.data || []).length
  } catch {
    // 忽略统计失败
  }
})
</script>
