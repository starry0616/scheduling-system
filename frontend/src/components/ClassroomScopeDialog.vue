<template>
  <el-dialog :model-value="modelValue" title="配置任务教室范围" width="820px" top="10vh" destroy-on-close
    @update:model-value="(v) => $emit('update:modelValue', v)">
    <div v-loading="loading" class="scope-body">
      <el-row :gutter="16">
        <el-col :span="12">
          <div class="block-title">已选教室 <span class="count">{{ staged.length }}</span></div>
          <el-table :data="staged" size="small" border max-height="340" empty-text="尚未选择教室">
            <el-table-column prop="roomNo" label="教室编号" min-width="110" />
            <el-table-column prop="building" label="楼栋" width="110" show-overflow-tooltip />
            <el-table-column prop="capacity" label="容量" width="70" align="center" />
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button link type="danger" :disabled="saving" @click="removeOne(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="12">
          <div class="block-title">候选教室(未选) <span class="count">{{ available.length }}</span></div>
          <el-table :data="available" size="small" border max-height="340" empty-text="暂无其他教室">
            <el-table-column prop="roomNo" label="教室编号" min-width="110" />
            <el-table-column prop="building" label="楼栋" width="110" show-overflow-tooltip />
            <el-table-column prop="capacity" label="容量" width="70" align="center" />
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="saving" @click="addOne(row)">添加</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
      <el-alert type="info" :closable="false" title="保存采用覆盖式替换语义：提交后将以上已选集合整体作为任务可用教室池。" style="margin-top: 10px" />
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
import { listTaskClassrooms, replaceTaskClassrooms } from '@/api/schedulingTask'
import { listClassrooms } from '@/api/resource'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  taskId: { type: [Number, String], required: true }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const saving = ref(false)
const options = ref([])
const staged = ref([])

const stagedIds = computed(() => new Set(staged.value.map((o) => o.id)))
const available = computed(() => options.value.filter((o) => !stagedIds.value.has(o.id)))

async function load() {
  loading.value = true
  try {
    const [optRes, curRes] = await Promise.all([
      listClassrooms(),
      listTaskClassrooms(props.taskId)
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
    await replaceTaskClassrooms(props.taskId, staged.value.map((o) => o.id))
    ElMessage.success('教室范围保存成功')
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
