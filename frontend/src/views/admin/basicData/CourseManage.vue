<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>课程管理</span>
          <div>
            <el-input v-model="query.keyword" placeholder="按课程代码/名称搜索" clearable style="width: 220px; margin-right: 8px"
              @keyup.enter="load" @clear="load" />
            <el-button @click="load" :icon="Refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增课程</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无课程">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="courseCode" label="课程代码" width="130" />
        <el-table-column prop="courseName" label="课程名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="课程类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="courseTypeMeta[row.courseType]?.type || 'info'" effect="light">
              {{ courseTypeMeta[row.courseType]?.label || row.courseType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="要求教室类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="roomTypeMeta[row.requiredRoomType]?.type || 'info'" effect="light">
              {{ roomTypeMeta[row.requiredRoomType]?.label || row.requiredRoomType }}
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
    <el-dialog :model-value="dialogVisible" :title="editing ? '编辑课程' : '新增课程'" width="560px" top="6vh"
      destroy-on-close @update:model-value="(v) => (dialogVisible = v)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="课程代码" prop="courseCode">
          <el-input v-model="form.courseCode" maxlength="20" placeholder="如: CS2201" clearable />
        </el-form-item>
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" maxlength="100" placeholder="如: 程序设计基础" clearable />
        </el-form-item>
        <el-form-item label="课程类型" prop="courseType">
          <el-select v-model="form.courseType" style="width: 100%">
            <el-option v-for="(v, k) in COURSE_TYPE" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="要求教室类型" prop="requiredRoomType">
          <el-select v-model="form.requiredRoomType" style="width: 100%">
            <el-option v-for="(v, k) in ROOM_TYPE" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <div class="form-tip">删除被开课实例引用的课程会被后端拒绝并提示原因。</div>
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
import { COURSE_TYPE, ROOM_TYPE } from '@/utils/dict'
import { listCourses, createCourse, updateCourse, deleteCourse } from '@/api/resource'

const loading = ref(false)
const rows = ref([])
const courseTypeMeta = COURSE_TYPE
const roomTypeMeta = ROOM_TYPE
const query = reactive({ keyword: '' })

async function load() {
  loading.value = true
  try {
    const res = await listCourses(query.keyword || undefined)
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
const form = reactive({ id: null, courseCode: '', courseName: '', courseType: 'THEORY', requiredRoomType: 'NORMAL' })

const rules = {
  courseCode: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  courseType: [{ required: true, message: '请选择课程类型', trigger: 'change' }],
  requiredRoomType: [{ required: true, message: '请选择教室类型', trigger: 'change' }]
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, courseCode: '', courseName: '', courseType: 'THEORY', requiredRoomType: 'NORMAL' })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    courseCode: row.courseCode,
    courseName: row.courseName,
    courseType: row.courseType,
    requiredRoomType: row.requiredRoomType
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
    courseCode: (form.courseCode || '').trim(),
    courseName: (form.courseName || '').trim(),
    courseType: form.courseType,
    requiredRoomType: form.requiredRoomType
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateCourse(form.id, payload)
      ElMessage.success('课程修改成功')
    } else {
      await createCourse(payload)
      ElMessage.success('课程新增成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 后端 400/500 原因已由 request 拦截器统一提示, 对话框保持打开便于修改
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗? 删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteCourse(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 被开课实例引用等业务错误由 request 拦截器弹出后端 message
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
}
</style>
