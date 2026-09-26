<template>
  <el-dialog :model-value="modelValue" title="配置任务课程范围" width="980px" top="6vh" destroy-on-close
    @update:model-value="(v) => $emit('update:modelValue', v)">
    <div v-loading="loading" class="scope-body">
      <el-row :gutter="16">
        <el-col :span="13">
          <div class="block-title">已选课程(开课实例) <span class="count">{{ staged.length }}</span></div>
          <el-table :data="staged" size="small" border max-height="360" empty-text="尚未选择课程">
            <el-table-column label="课程" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.courseName }}</template>
            </el-table-column>
            <el-table-column label="教师" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ row.teacherName }}</template>
            </el-table-column>
            <el-table-column label="班级" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.classNames || []).join('、') || '-' }}</template>
            </el-table-column>
            <el-table-column label="周课次" width="70" align="center">
              <template #default="{ row }">{{ row.weeklySessions }}</template>
            </el-table-column>
            <el-table-column label="每次节数" width="80" align="center">
              <template #default="{ row }">{{ row.durationSlots }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button link type="danger" :disabled="saving" @click="removeOne(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="11">
          <div class="block-title">候选课程(同学期, 未选) <span class="count">{{ available.length }}</span></div>
          <el-table :data="available" size="small" border max-height="360" empty-text="同学期暂无其他开课">
            <el-table-column label="课程" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.courseName }}</template>
            </el-table-column>
            <el-table-column label="教师" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ row.teacherName }}</template>
            </el-table-column>
            <el-table-column label="班级" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.classNames || []).join('、') || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="saving" @click="addOne(row)">添加</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
      <el-alert type="info" :closable="false" title="保存采用覆盖式替换语义：提交后将以上已选集合整体作为任务课程范围。" style="margin-top: 10px" />
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listTaskCourseOfferings, getTaskOfferingOptions, replaceTaskCourseOfferings } from '@/api/schedulingTask'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  taskId: { type: [Number, String], required: true }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const saving = ref(false)
const options = ref([]) // 同学期全部开课候选
const staged = ref([]) // 已选开课实例(对象)

// staged id 集合, 用于计算候选
const stagedIds = computed(() => new Set(staged.value.map((o) => o.id)))
const available = computed(() => options.value.filter((o) => !stagedIds.value.has(o.id)))

async function load() {
  loading.value = true
  try {
    const [optRes, curRes] = await Promise.all([
      getTaskOfferingOptions(props.taskId),
      listTaskCourseOfferings(props.taskId)
    ])
    options.value = optRes.data || []
    const cur = curRes.data || []
    const idSet = new Set(cur.map((o) => o.id))
    staged.value = options.value.filter((o) => idSet.has(o.id))
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) load()
  }
)

function addOne(row) {
  staged.value.push(row)
}
function removeOne(row) {
  staged.value = staged.value.filter((o) => o.id !== row.id)
}

async function save() {
  saving.value = true
  try {
    await replaceTaskCourseOfferings(props.taskId, staged.value.map((o) => o.id))
    ElMessage.success('课程范围保存成功')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.scope-body {
  min-height: 120px;
}
.block-title {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 500;
}
.count {
  color: #409eff;
  margin-left: 4px;
}
</style>
