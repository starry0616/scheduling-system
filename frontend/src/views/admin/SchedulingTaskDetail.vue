<template>
  <div class="page" v-loading="loading">
    <el-page-header content="排课任务详情" @back="$router.push('/admin/tasks')" />

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>
            基本信息
            <el-tag v-if="task" :type="statusMeta[task.status]?.type || 'info'" style="margin-left: 8px">
              {{ statusMeta[task.status]?.label || task.status }}
            </el-tag>
          </span>
          <div class="op-bar">
            <el-button size="small" :icon="Refresh" @click="loadAll">刷新</el-button>
            <template v-if="task && task.status === 'PENDING'">
              <el-button size="small" type="primary" :icon="Edit" @click="editVisible = true">编辑任务</el-button>
              <el-button size="small" type="primary" plain :icon="Operation" @click="courseScopeVisible = true">配置课程</el-button>
              <el-button size="small" type="primary" plain :icon="OfficeBuilding" @click="roomScopeVisible = true">配置教室</el-button>
              <el-button size="small" type="warning" :icon="VideoPlay" :loading="running" @click="handleRun">开始排课</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="handleDelete">删除</el-button>
            </template>
            <template v-else-if="task && task.status === 'COMPLETED'">
              <el-button size="small" type="success" :icon="DataLine" @click="$router.push(`/admin/tasks/${task.id}/result`)">查看结果</el-button>
              <el-button size="small" type="primary" plain :icon="Calendar" @click="$router.push(`/admin/tasks/${task.id}/schedule`)">查看课表</el-button>
            </template>
          </div>
        </div>
      </template>
      <template v-if="task">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务名称">{{ task.taskName }}</el-descriptions-item>
          <el-descriptions-item label="学期">{{ task.semester }}</el-descriptions-item>
          <el-descriptions-item label="总周数">{{ task.weekCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(task.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ fmtTime(task.finishTime) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusMeta[task.status]?.label || task.status }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="task.status === 'FAILED'" type="error" :closable="false" show-icon
          title="该任务排课失败。后端当前仅允许删除 PENDING 状态任务，失败任务无法通过本页删除；请刷新查看状态。" style="margin-top: 12px" />
        <el-alert v-if="task.status === 'RUNNING'" type="warning" :closable="false" show-icon
          title="该任务处于排课中(RUNNING)。排课接口为同步执行，若页面刷新后仍处于该状态，请联系管理员确认。" style="margin-top: 12px" />
      </template>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>模拟退火算法参数</template>
          <el-descriptions v-if="task" :column="1" border size="small">
            <el-descriptions-item label="初始温度上限">{{ task.maxInitialTemp ?? '默认 1000' }}</el-descriptions-item>
            <el-descriptions-item label="初始温度下限">{{ task.minInitialTemp ?? '默认 10' }}</el-descriptions-item>
            <el-descriptions-item label="终止温度">{{ task.minTemp ?? '默认 0.1' }}</el-descriptions-item>
            <el-descriptions-item label="降温系数">{{ task.coolingRate ?? '默认 0.95' }}</el-descriptions-item>
            <el-descriptions-item label="温度迭代次数">{{ task.maxTempIterations ?? '默认 5000' }}</el-descriptions-item>
            <el-descriptions-item label="每温度邻域评价次数">{{ task.neighborsPerTemp ?? '默认 20' }}</el-descriptions-item>
            <el-descriptions-item label="冲突修复最大轮数">{{ task.maxRepairAttempts ?? '默认 3' }}</el-descriptions-item>
            <el-descriptions-item label="随机种子">{{ task.randomSeed ?? '自动(系统时间)' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>软约束权重</template>
          <el-descriptions v-if="task" :column="1" border size="small">
            <el-descriptions-item label="S1 教师时间偏好">{{ task.wTeacherPreference ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="S2 日期分布均衡">{{ task.wCourseDistribution ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="S3 学生每日课量均衡">{{ task.wStudentBalance ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="S4 教师连续课节">{{ task.wTeacherContinuous ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="S5 学生空课时间">{{ task.wStudentIdle ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="S6 早晚节避免">{{ task.wMorningEvening ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="硬约束权重 W_hard(后端计算)">
              {{ task.hardConstraintWeight ?? '未执行(执行时自动计算)' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>课程范围(开课实例)</span>
              <el-button v-if="task && task.status === 'PENDING'" size="small" type="primary" plain @click="courseScopeVisible = true">配置课程</el-button>
            </div>
          </template>
          <el-table :data="offerings" size="small" border empty-text="尚未配置课程，请点击「配置课程」添加开课实例">
            <el-table-column label="课程名称" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.course?.courseName || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="课程编号" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.course?.courseCode || '-' }}</template>
            </el-table-column>
            <el-table-column label="课程类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.course?.courseType === 'LAB' ? 'warning' : 'info'">
                  {{ row.course?.courseType === 'LAB' ? '实验' : '理论' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="教师" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ row.teacher?.name || '-' }}</template>
            </el-table-column>
            <el-table-column label="授课班级" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <template v-if="(row.classes || []).length">
                  <el-tag v-for="c in row.classes" :key="c.id" size="small" style="margin-right: 4px">{{ c.className }}</el-tag>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="周课次" width="75" align="center">
              <template #default="{ row }">{{ row.offering?.weeklySessions }}</template>
            </el-table-column>
            <el-table-column label="每次节数" width="85" align="center">
              <template #default="{ row }">{{ row.offering?.durationSlots }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>教室范围</span>
              <el-button v-if="task && task.status === 'PENDING'" size="small" type="primary" plain @click="roomScopeVisible = true">配置教室</el-button>
            </div>
          </template>
          <el-table :data="classrooms" size="small" border empty-text="尚未配置教室，请点击「配置教室」添加教室">
            <el-table-column label="教室编号" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.roomNo }}</template>
            </el-table-column>
            <el-table-column prop="building" label="教学楼" min-width="100" show-overflow-tooltip />
            <el-table-column prop="capacity" label="容量" width="70" align="center" />
            <el-table-column label="类型" width="110" align="center">
              <template #default="{ row }">{{ roomTypeLabel(row.roomType) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑对话框 / 范围配置对话框 -->
    <TaskFormDialog v-model="editVisible" :record="task" :submit-handler="handleEditSubmit" @saved="loadAll" />
    <CourseScopeDialog v-model="courseScopeVisible" :task-id="taskId" @saved="loadAll" />
    <ClassroomScopeDialog v-model="roomScopeVisible" :task-id="taskId" @saved="loadAll" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Edit, Operation, OfficeBuilding, VideoPlay, Delete, DataLine, Calendar } from '@element-plus/icons-vue'
import { getSchedulingTask, getSchedulingTaskData, updateSchedulingTask, deleteSchedulingTask, runSchedulingTask } from '@/api/schedulingTask'
import { TASK_STATUS, roomTypeLabel } from '@/utils/dict'
import { outcomeHint } from '@/utils/runHint'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import CourseScopeDialog from '@/components/CourseScopeDialog.vue'
import ClassroomScopeDialog from '@/components/ClassroomScopeDialog.vue'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id

const statusMeta = TASK_STATUS
const loading = ref(false)
const task = ref(null)
const offerings = ref([])
const classrooms = ref([])

const editVisible = ref(false)
const courseScopeVisible = ref(false)
const roomScopeVisible = ref(false)
const running = ref(false)

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 19) : '')

async function loadAll() {
  loading.value = true
  try {
    const [detailRes, dataRes] = await Promise.all([
      getSchedulingTask(taskId),
      getSchedulingTaskData(taskId)
    ])
    task.value = detailRes.data || null
    const d = dataRes.data || {}
    offerings.value = d.offerings || []
    classrooms.value = d.classrooms || []
  } finally {
    loading.value = false
  }
}

const handleEditSubmit = async (payload) => {
  await updateSchedulingTask(taskId, payload)
  ElMessage.success('任务修改成功')
}

async function handleRun() {
  try {
    await ElMessageBox.confirm('确定开始执行自动排课吗?', '开始排课', {
      type: 'warning',
      confirmButtonText: '开始排课',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  running.value = true
  try {
    const res = await runSchedulingTask(taskId)
    const hint = outcomeHint(res.data)
    if (hint) ElMessage.success(hint)
    await loadAll()
  } catch {
    // 具体失败原因(如数据不可行)已由 request 拦截器展示, 此处刷新任务状态(FAILED)
    await loadAll()
  } finally {
    running.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除该排课任务吗? 删除后不可恢复。', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteSchedulingTask(taskId)
  ElMessage.success('删除成功')
  router.push('/admin/tasks')
}

onMounted(loadAll)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.op-bar {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
