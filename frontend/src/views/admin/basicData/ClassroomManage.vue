<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>教室管理</span>
          <div>
            <el-input v-model="query.keyword" placeholder="按教室编号/楼栋搜索" clearable style="width: 220px; margin-right: 8px"
              @keyup.enter="load" @clear="load" />
            <el-button @click="load" :icon="Refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增教室</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无教室">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roomNo" label="教室编号" width="120" />
        <el-table-column prop="building" label="楼栋" min-width="120">
          <template #default="{ row }">{{ row.building || '-' }}</template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量(人)" width="100" align="center">
          <template #default="{ row }">{{ row.capacity }}</template>
        </el-table-column>
        <el-table-column label="教室类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="roomTypeMeta[row.roomType]?.type || 'info'" effect="light">
              {{ roomTypeMeta[row.roomType]?.label || row.roomType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 对话框 -->
    <el-dialog :model-value="dialogVisible" :title="editing ? '编辑教室' : '新增教室'" width="560px" top="6vh"
      destroy-on-close @update:model-value="(v) => (dialogVisible = v)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="教室编号" prop="roomNo">
          <el-input v-model="form.roomNo" maxlength="20" placeholder="如: N201 / L101" clearable />
        </el-form-item>
        <el-form-item label="楼栋" prop="building">
          <el-input v-model="form.building" maxlength="50" placeholder="如: 教学楼B / 实验楼A" clearable />
        </el-form-item>
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="10000" style="width: 200px" />
          <div class="form-tip">容量至少 1 人。开课班级人数超过教室容量时将无法安排该教室。</div>
        </el-form-item>
        <el-form-item label="教室类型" prop="roomType">
          <el-select v-model="form.roomType" style="width: 100%">
            <el-option v-for="(v, k) in ROOM_TYPE" :key="k" :label="v.label" :value="k" />
          </el-select>
          <div class="form-tip">普通教室 / 多媒体教室 / 机房实验室。实验课(连续2节)只能安排在机房实验室。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ROOM_TYPE } from '@/utils/dict'
import { listClassrooms, createClassroom, updateClassroom, deleteClassroom } from '@/api/resource'

const roomTypeMeta = ROOM_TYPE
const loading = ref(false)
const rows = ref([])
const query = reactive({ keyword: '' })

async function load() {
  loading.value = true
  try {
    const res = await listClassrooms(query.keyword || undefined)
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

// ---- 新增 / 编辑 ----
const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({ id: null, roomNo: '', building: '', capacity: 60, roomType: 'NORMAL' })

const rules = {
  roomNo: [{ required: true, message: '请输入教室编号', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入教室容量', trigger: 'blur' }],
  roomType: [{ required: true, message: '请选择教室类型', trigger: 'change' }]
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, roomNo: '', building: '', capacity: 60, roomType: 'NORMAL' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    roomNo: row.roomNo,
    building: row.building || '',
    capacity: row.capacity,
    roomType: row.roomType
  })
  dialogVisible.value = true
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
    roomNo: (form.roomNo || '').trim(),
    building: (form.building || '').trim() || null,
    capacity: form.capacity,
    roomType: form.roomType
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateClassroom(form.id, payload)
      ElMessage.success('教室修改成功')
    } else {
      await createClassroom(payload)
      ElMessage.success('教室新增成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 教室编号重复等业务错误由 request 拦截器弹出后端 message
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除教室「${row.roomNo}」吗? 删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteClassroom(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 被排课任务范围引用等业务错误由 request 拦截器弹出后端 message
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
  width: 100%;
}
</style>
