<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>班级管理</span>
          <div>
            <el-input v-model="query.keyword" placeholder="按班级名称搜索" clearable style="width: 220px; margin-right: 8px"
              @keyup.enter="load" @clear="load" />
            <el-button @click="load" :icon="Refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增班级</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无班级">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="className" label="班级名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="grade" label="年级" width="100" align="center">
          <template #default="{ row }">{{ row.grade || '-' }}</template>
        </el-table-column>
        <el-table-column prop="studentCount" label="人数" width="90" align="center">
          <template #default="{ row }">{{ row.studentCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="department" label="院系" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.department || '-' }}</template>
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
    <el-dialog :model-value="dialogVisible" :title="editing ? '编辑班级' : '新增班级'" width="560px" top="6vh"
      destroy-on-close @update:model-value="(v) => (dialogVisible = v)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="form.className" maxlength="50" placeholder="如: 软件工程2401" clearable />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-select v-model="form.grade" filterable allow-create default-first-option style="width: 100%"
            placeholder="选择或输入年级">
            <el-option v-for="g in GRADE_OPTIONS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="人数" prop="studentCount">
          <el-input-number v-model="form.studentCount" :min="0" :max="1000" style="width: 200px" />
        </el-form-item>
        <el-form-item label="院系" prop="department">
          <el-input v-model="form.department" maxlength="100" placeholder="如: 软件学院" clearable />
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
import { listClasses, createClazz, updateClazz, deleteClazz } from '@/api/resource'

const GRADE_OPTIONS = ['2026级', '2025级', '2024级', '2023级', '2022级']

const loading = ref(false)
const rows = ref([])
const query = reactive({ keyword: '' })

async function load() {
  loading.value = true
  try {
    const res = await listClasses(query.keyword || undefined)
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
const form = reactive({ id: null, className: '', grade: '', studentCount: null, department: '' })

const rules = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  studentCount: [{ required: true, message: '请输入班级人数', trigger: 'blur' }]
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, className: '', grade: '2024级', studentCount: null, department: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    className: row.className,
    grade: row.grade || '',
    studentCount: row.studentCount,
    department: row.department || ''
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
    className: (form.className || '').trim(),
    grade: (form.grade || '').trim() || null,
    studentCount: form.studentCount,
    department: (form.department || '').trim() || null
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateClazz(form.id, payload)
      ElMessage.success('班级修改成功')
    } else {
      await createClazz(payload)
      ElMessage.success('班级新增成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 名称重复等业务错误由 request 拦截器弹出后端 message
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除班级「${row.className}」吗? 删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteClazz(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 被开课实例/排课引用等业务错误由 request 拦截器弹出后端 message
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
</style>
