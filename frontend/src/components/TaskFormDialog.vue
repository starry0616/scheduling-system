<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="760px"
    top="6vh"
    destroy-on-close
    @update:model-value="(v) => $emit('update:modelValue', v)"
  >
    <el-form ref="formRef" :model="model" label-width="150px" class="task-form">
      <el-divider content-position="left">基本信息</el-divider>
      <el-form-item label="任务名称" prop="taskName"
        :rules="[{ required: true, message: '请输入任务名称', trigger: 'blur' }]">
        <el-input v-model="model.taskName" maxlength="100" placeholder="如: 2026秋季学期全校排课" clearable />
      </el-form-item>
      <el-form-item label="学期" prop="semester"
        :rules="[{ required: true, message: '请选择学期', trigger: 'change' }]">
        <el-select v-model="model.semester" filterable allow-create default-first-option style="width: 100%"
          placeholder="选择或输入学期">
          <el-option label="2026秋" value="2026秋" />
          <el-option label="2026春" value="2026春" />
          <el-option label="2025秋" value="2025秋" />
          <el-option label="2025春" value="2025春" />
        </el-select>
      </el-form-item>
      <el-form-item label="总周数" prop="weekCount">
        <el-input-number v-model="model.weekCount" :min="1" :max="60" style="width: 200px" placeholder="默认 16" />
        <span class="form-hint">留空使用默认 16 周</span>
      </el-form-item>

      <el-divider content-position="left">模拟退火参数(SA)</el-divider>
      <div class="param-grid">
        <el-form-item label="初始温度上限" prop="maxInitialTemp">
          <el-input-number v-model="model.maxInitialTemp" :min="0.01" :step="100" :precision="2" controls-position="right"
            style="width: 180px" placeholder="默认 1000" />
        </el-form-item>
        <el-form-item label="初始温度下限" prop="minInitialTemp">
          <el-input-number v-model="model.minInitialTemp" :min="0.01" :step="1" :precision="2" controls-position="right"
            style="width: 180px" placeholder="默认 10" />
        </el-form-item>
        <el-form-item label="终止温度" prop="minTemp">
          <el-input-number v-model="model.minTemp" :min="0.001" :step="0.1" :precision="3" controls-position="right"
            style="width: 180px" placeholder="默认 0.1" />
        </el-form-item>
        <el-form-item label="降温系数" prop="coolingRate">
          <el-input-number v-model="model.coolingRate" :min="0.01" :max="0.99" :step="0.01" :precision="2" controls-position="right"
            style="width: 180px" placeholder="默认 0.95" />
        </el-form-item>
        <el-form-item label="温度迭代次数" prop="maxTempIterations">
          <el-input-number v-model="model.maxTempIterations" :min="1" :step="500" controls-position="right"
            style="width: 180px" placeholder="默认 5000" />
        </el-form-item>
        <el-form-item label="每温度邻域评价次数" prop="neighborsPerTemp">
          <el-input-number v-model="model.neighborsPerTemp" :min="1" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 20" />
        </el-form-item>
        <el-form-item label="冲突修复最大轮数" prop="maxRepairAttempts">
          <el-input-number v-model="model.maxRepairAttempts" :min="0" :step="1" controls-position="right"
            style="width: 180px" placeholder="默认 3" />
        </el-form-item>
        <el-form-item label="随机种子" prop="randomSeed">
          <el-input-number v-model="model.randomSeed" :min="0" :step="1" :controls="false" style="width: 180px"
            placeholder="留空=系统时间" />
        </el-form-item>
      </div>
      <el-alert type="info" :closable="false" show-icon
        title="以上参数留空时新建任务使用系统默认值；编辑任务时留空表示保持现值。" style="margin-bottom: 12px" />

      <el-divider content-position="left">软约束权重</el-divider>
      <div class="param-grid">
        <el-form-item label="S1 教师时间偏好" prop="wTeacherPreference">
          <el-input-number v-model="model.wTeacherPreference" :min="0" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 50" />
        </el-form-item>
        <el-form-item label="S2 日期分布均衡" prop="wCourseDistribution">
          <el-input-number v-model="model.wCourseDistribution" :min="0" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 30" />
        </el-form-item>
        <el-form-item label="S3 学生每日课量均衡" prop="wStudentBalance">
          <el-input-number v-model="model.wStudentBalance" :min="0" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 25" />
        </el-form-item>
        <el-form-item label="S4 教师连续课节" prop="wTeacherContinuous">
          <el-input-number v-model="model.wTeacherContinuous" :min="0" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 30" />
        </el-form-item>
        <el-form-item label="S5 学生空课时间" prop="wStudentIdle">
          <el-input-number v-model="model.wStudentIdle" :min="0" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 25" />
        </el-form-item>
        <el-form-item label="S6 早晚节避免" prop="wMorningEvening">
          <el-input-number v-model="model.wMorningEvening" :min="0" :step="5" controls-position="right"
            style="width: 180px" placeholder="默认 15" />
        </el-form-item>
      </div>
      <el-alert type="info" :closable="false" show-icon
        title="硬约束权重 W_hard 由后端在排课执行时自动计算(hardConstraintWeight)，无需前端设置。" style="margin-bottom: 4px" />
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 编辑时传入当前任务对象; 新建为 null
  record: { type: Object, default: null },
  // 提交处理函数: async (payload) => void; 抛错则不关闭对话框
  submitHandler: { type: Function, required: true }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref()
const saving = ref(false)

const model = reactive({
  taskName: '',
  semester: '',
  weekCount: null,
  maxInitialTemp: null,
  minInitialTemp: null,
  minTemp: null,
  coolingRate: null,
  maxTempIterations: null,
  neighborsPerTemp: null,
  maxRepairAttempts: null,
  randomSeed: null,
  wTeacherPreference: null,
  wCourseDistribution: null,
  wStudentBalance: null,
  wTeacherContinuous: null,
  wStudentIdle: null,
  wMorningEvening: null
})

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const r = props.record || {}
    Object.keys(model).forEach((key) => {
      model[key] = r[key] === undefined || r[key] === null ? null : r[key]
    })
    if (!props.record) {
      model.semester = '2026秋'
    }
  }
)

function toNumber(value) {
  if (value === '' || value === null || value === undefined || Number.isNaN(Number(value))) return undefined
  return Number(value)
}

async function handleSubmit() {
  if (formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
  }
  const payload = {
    taskName: (model.taskName || '').trim(),
    semester: (model.semester || '').trim(),
    weekCount: toNumber(model.weekCount),
    maxInitialTemp: toNumber(model.maxInitialTemp),
    minInitialTemp: toNumber(model.minInitialTemp),
    minTemp: toNumber(model.minTemp),
    coolingRate: toNumber(model.coolingRate),
    maxTempIterations: toNumber(model.maxTempIterations),
    neighborsPerTemp: toNumber(model.neighborsPerTemp),
    maxRepairAttempts: toNumber(model.maxRepairAttempts),
    randomSeed: toNumber(model.randomSeed),
    wTeacherPreference: toNumber(model.wTeacherPreference),
    wCourseDistribution: toNumber(model.wCourseDistribution),
    wStudentBalance: toNumber(model.wStudentBalance),
    wTeacherContinuous: toNumber(model.wTeacherContinuous),
    wStudentIdle: toNumber(model.wStudentIdle),
    wMorningEvening: toNumber(model.wMorningEvening)
  }
  if (!payload.taskName) {
    ElMessage.warning('请输入任务名称')
    return
  }
  if (!payload.semester) {
    ElMessage.warning('请选择学期')
    return
  }
  // 冷却系数/温度等边界轻校验(避免把明显非法值发往后端)
  if (payload.coolingRate !== undefined && (payload.coolingRate <= 0 || payload.coolingRate >= 1)) {
    ElMessage.warning('降温系数必须在 0 与 1 之间')
    return
  }
  saving.value = true
  try {
    await props.submitHandler(payload)
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.param-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 16px;
}
.form-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
