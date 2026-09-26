<template>
  <div class="page">
    <!-- 概览统计: 让管理员一眼看到任务状态分布 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="5">
        <el-card shadow="never">
          <div class="stat-title">全部任务</div>
          <div class="stat-num">{{ counts.total }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never">
          <div class="stat-title">待排课</div>
          <div class="stat-num stat-pending">{{ counts.PENDING }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never">
          <div class="stat-title">排课中</div>
          <div class="stat-num stat-running">{{ counts.RUNNING }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never">
          <div class="stat-title">已完成</div>
          <div class="stat-num stat-completed">{{ counts.COMPLETED }}</div>
        </el-card>
      </el-col>
      <el-col :span="5">
        <el-card shadow="never">
          <div class="stat-title">排课失败</div>
          <div class="stat-num stat-failed">{{ counts.FAILED }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>排课任务列表</span>
          <div>
            <el-input v-model="query.semester" placeholder="按学期过滤, 如 2026秋" clearable style="width: 200px; margin-right: 8px"
              @keyup.enter="load" />
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px; margin-right: 8px" @change="load">
              <el-option v-for="(v, k) in statusMeta" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-button @click="load" :icon="Refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新建任务</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tasks" v-loading="loading" empty-text="暂无排课任务">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="semester" label="学期" width="100" />
        <el-table-column prop="weekCount" label="周数" width="70" align="center">
          <template #default="{ row }">{{ row.weekCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta[row.status]?.type || 'info'" effect="light">{{ statusMeta[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.finishTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">查看</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" :loading="runningId === row.id" @click="handleRun(row)">开始排课</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else-if="row.status === 'COMPLETED'">
              <el-button link type="success" @click="goResult(row)">查看结果</el-button>
              <el-button link type="primary" @click="goSchedule(row)">课表</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑 对话框 -->
    <TaskFormDialog
      v-model="dialogVisible"
      :record="editingRecord"
      :submit-handler="submitHandler"
      @saved="onSaved"
    />
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { listSchedulingTasks, createSchedulingTask, updateSchedulingTask, deleteSchedulingTask, runSchedulingTask } from '@/api/schedulingTask'
import { TASK_STATUS } from '@/utils/dict'
import { outcomeHint } from '@/utils/runHint'
import TaskFormDialog from '@/components/TaskFormDialog.vue'

const router = useRouter()
const loading = ref(false)
const tasks = ref([])
const statusMeta = TASK_STATUS

const query = reactive({ semester: '', status: '' })

// 汇总(基于全部任务实时统计)
const counts = reactive({ total: 0, PENDING: 0, RUNNING: 0, COMPLETED: 0, FAILED: 0 })
function computeCounts(rows) {
  counts.total = rows.length
  counts.PENDING = counts.RUNNING = counts.COMPLETED = counts.FAILED = 0
  rows.forEach((r) => {
    if (counts[r.status] !== undefined) counts[r.status]++
  })
}

async function load() {
  loading.value = true
  try {
    const res = await listSchedulingTasks({ semester: query.semester || undefined, status: query.status || undefined })
    tasks.value = res.data || []
    computeCounts(res.data || [])
  } finally {
    loading.value = false
  }
}

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

function goDetail(row) {
  router.push(`/admin/tasks/${row.id}`)
}
function goResult(row) {
  router.push(`/admin/tasks/${row.id}/result`)
}
function goSchedule(row) {
  router.push(`/admin/tasks/${row.id}/schedule`)
}

// ---- 新建 / 编辑 ----
const dialogVisible = ref(false)
const editingRecord = ref(null)

function openCreate() {
  editingRecord.value = null
  dialogVisible.value = true
}
function openEdit(row) {
  editingRecord.value = { ...row }
  dialogVisible.value = true
}

const submitHandler = async (payload) => {
  if (editingRecord.value) {
    await updateSchedulingTask(editingRecord.value.id, payload)
    ElMessage.success('任务修改成功')
  } else {
    await createSchedulingTask(payload)
    ElMessage.success('任务创建成功')
  }
}
function onSaved() {
  load()
}

// ---- 执行排课 ----
const runningId = ref(null)
async function handleRun(row) {
  try {
    await ElMessageBox.confirm('确定开始执行自动排课吗?', '开始排课', {
      type: 'warning',
      confirmButtonText: '开始排课',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  runningId.value = row.id
  try {
    const res = await runSchedulingTask(row.id)
    const hint = outcomeHint(res.data)
    if (hint) ElMessage.success(hint)
    ElMessage.success('排课执行完成')
    await load()
  } catch (e) {
    // 后端 400/500 的具体原因已由 request 拦截器提示
    await load()
  } finally {
    runningId.value = null
  }
}

// ---- 删除 ----
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除排课任务「${row.taskName}」吗? 删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteSchedulingTask(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}
.stat-title {
  font-size: 13px;
  color: #909399;
}
.stat-num {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
.stat-pending {
  color: #909399;
}
.stat-running {
  color: #e6a23c;
}
.stat-completed {
  color: #67c23a;
}
.stat-failed {
  color: #f56c6c;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
