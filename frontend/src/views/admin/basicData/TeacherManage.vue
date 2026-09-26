<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>教师管理</span>
          <div>
            <el-input v-model="query.keyword" placeholder="按工号/姓名搜索" clearable style="width: 220px; margin-right: 8px"
              @keyup.enter="load" @clear="load" />
            <el-button @click="load" :icon="Refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增教师</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无教师">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="teacherNo" label="工号" width="120" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="title" label="职称" width="120">
          <template #default="{ row }">{{ row.title || '-' }}</template>
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
    <el-dialog :model-value="dialogVisible" :title="editing ? '编辑教师' : '新增教师'" width="560px" top="6vh"
      destroy-on-close @update:model-value="(v) => (dialogVisible = v)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="关联账号" prop="userId">
          <el-select v-model="form.userId" filterable placeholder="选择可绑定的教师账号" style="width: 100%"
            :disabled="!candidatesLoaded">
            <el-option v-for="c in candidateOptions" :key="c.id" :label="`${c.realName}(${c.username})`" :value="c.id" />
          </el-select>
          <div class="form-tip">教师必须绑定一个教师登录账号(每人只能绑定一个教师档案)。列表仅显示未被占用的教师账号。</div>
        </el-form-item>
        <el-form-item label="教师工号" prop="teacherNo">
          <el-input v-model="form.teacherNo" maxlength="20" placeholder="如: T2026001" clearable />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="请输入教师姓名" clearable />
        </el-form-item>
        <el-form-item label="职称" prop="title">
          <el-input v-model="form.title" maxlength="50" placeholder="如: 教授 / 副教授 / 讲师" clearable />
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
import { reactive, ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { listTeachers, createTeacher, updateTeacher, deleteTeacher, listTeacherCandidates } from '@/api/resource'

const loading = ref(false)
const rows = ref([])
const query = reactive({ keyword: '' })

async function load() {
  loading.value = true
  try {
    const res = await listTeachers(query.keyword || undefined)
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
const candidates = ref([])
const candidatesLoaded = ref(false)
const form = reactive({ id: null, userId: null, teacherNo: '', name: '', title: '', department: '' })

// 编辑时并入当前绑定账号, 保证"保持不变"可选
const candidateOptions = computed(() => {
  const opts = candidates.value.slice()
  if (editing.value && form.userId && !opts.some((c) => c.id === form.userId)) {
    opts.unshift({ id: form.userId, realName: `当前账号(#${form.userId})`, username: '保持原账号' })
  }
  return opts
})

const rules = {
  userId: [{ required: true, message: '请选择关联账号', trigger: 'change' }],
  teacherNo: [{ required: true, message: '请输入教师工号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入教师姓名', trigger: 'blur' }]
}

async function openCreate() {
  editing.value = false
  candidatesLoaded.value = false
  candidates.value = []
  Object.assign(form, { id: null, userId: null, teacherNo: '', name: '', title: '', department: '' })
  dialogVisible.value = true
  const res = await listTeacherCandidates()
  candidates.value = res.data || []
  candidatesLoaded.value = true
}

async function openEdit(row) {
  editing.value = true
  candidatesLoaded.value = false
  candidates.value = []
  Object.assign(form, {
    id: row.id,
    userId: row.userId,
    teacherNo: row.teacherNo,
    name: row.name,
    title: row.title || '',
    department: row.department || ''
  })
  dialogVisible.value = true
  const res = await listTeacherCandidates(row.id)
  candidates.value = res.data || []
  candidatesLoaded.value = true
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
    userId: form.userId,
    teacherNo: (form.teacherNo || '').trim(),
    name: (form.name || '').trim(),
    title: (form.title || '').trim() || null,
    department: (form.department || '').trim() || null
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateTeacher(form.id, payload)
      ElMessage.success('教师修改成功')
    } else {
      await createTeacher(payload)
      ElMessage.success('教师新增成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 账号已绑定 / 工号重复等业务错误由 request 拦截器弹出后端 message
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除教师「${row.name}」吗? 删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteTeacher(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 被开课实例引用等业务错误由 request 拦截器弹出后端 message(不绕过前端隐藏, 但以后端为准)
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
}
</style>
