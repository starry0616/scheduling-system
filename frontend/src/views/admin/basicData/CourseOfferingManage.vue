<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>开课实例管理</span>
          <div>
            <el-select v-model="query.semester" placeholder="按学期过滤(留空=全部)" clearable filterable allow-create
              default-first-option style="width: 180px; margin-right: 8px" @change="load">
              <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
            </el-select>
            <el-button @click="load" :icon="Refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增开课实例</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" v-loading="loading" empty-text="暂无开课实例" @expand-change="handleExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-panel">
              <span class="expand-label">关联班级:</span>
              <template v-if="row._loadingClasses">加载中...</template>
              <template v-else-if="row._classes && row._classes.length">
                <el-tag v-for="c in row._classes" :key="c.id" size="small" class="class-tag">
                  {{ c.className }}
                </el-tag>
              </template>
              <span v-else class="expand-empty">尚未关联班级(编辑可添加)</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="课程" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.courseName }}</template>
        </el-table-column>
        <el-table-column label="教师" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.teacherName }}</template>
        </el-table-column>
        <el-table-column prop="semester" label="学期" width="90" align="center" />
        <el-table-column prop="weeklySessions" label="周次数" width="90" align="center">
          <template #default="{ row }">{{ row.weeklySessions }} 次/周</template>
        </el-table-column>
        <el-table-column label="每次节数" width="110" align="center">
          <template #default="{ row }">{{ row.durationSlots }} 节连续</template>
        </el-table-column>
        <el-table-column label="实验课" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="isLab(row) ? 'warning' : 'info'" effect="light">
              {{ isLab(row) ? '实验' : '理论' }}
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
    <el-dialog :model-value="dialogVisible" :title="editing ? '编辑开课实例' : '新增开课实例'" width="720px" top="4vh"
      destroy-on-close @update:model-value="(v) => (dialogVisible = v)">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="课程" prop="courseId">
              <el-select v-model="form.courseId" filterable placeholder="选择课程" style="width: 100%">
                <el-option v-for="o in courseOptions" :key="o.id"
                  :label="`${o.courseName}(${o.courseCode})`" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授课教师" prop="teacherId">
              <el-select v-model="form.teacherId" filterable placeholder="选择教师" style="width: 100%">
                <el-option v-for="t in teacherOptions" :key="t.id"
                  :label="`${t.name}(${t.teacherNo})`" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="学期" prop="semester">
          <el-select v-model="form.semester" filterable allow-create default-first-option style="width: 260px">
            <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="每周上课次数" prop="weeklySessions">
              <el-input-number v-model="form.weeklySessions" :min="1" :max="8" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每次连续节数" prop="durationSlots">
              <el-input-number v-model="form.durationSlots" :min="1" :max="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <div class="form-tip">
            语义约定: weeklySessions = 每周上课次数; durationSlots = 每次连续大节数(算法的连续时段依据)。
            设置连续节数 > 1 时后端会把该开课识别为实验课(需要机房实验室、仅可安排连续时段), 不能只用"是否实验课"代替节数配置。
          </div>
        </el-form-item>
        <el-form-item label="关联班级" prop="classIds">
          <el-select v-model="form.classIds" multiple filterable placeholder="选择要上课的班级(可多选)" style="width: 100%">
            <el-option v-for="c in classOptions" :key="c.id"
              :label="`${c.className}${c.grade ? '（' + c.grade + '）' : ''}`" :value="c.id" />
          </el-select>
          <div class="form-tip">保存时通过开课实例-班级关联接口覆盖保存; 不选则视为暂无班级。</div>
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
import {
  listCourses, listTeachers, listClasses,
  listCourseOfferings, createCourseOffering, updateCourseOffering, deleteCourseOffering,
  listOfferingClasses, replaceOfferingClasses
} from '@/api/resource'

const loading = ref(false)
const rows = ref([])
const query = reactive({ semester: '' })

const semesterOptions = computed(() => {
  const set = new Set(['2026秋'])
  rows.value.forEach((r) => r.semester && set.add(r.semester))
  return [...set]
})

const isLab = (row) => row.isLabCourse === true || row.durationSlots > 1

async function load() {
  loading.value = true
  try {
    const res = await listCourseOfferings(query.semester || undefined)
    rows.value = (res.data || []).map((r) => ({ ...r, _classes: null, _loadingClasses: false }))
  } finally {
    loading.value = false
  }
}

// ---- 展开行查看关联班级 ----
async function handleExpand(row) {
  if (row._classes !== null) return
  row._loadingClasses = true
  try {
    const res = await listOfferingClasses(row.id)
    row._classes = res.data || []
  } catch {
    row._classes = []
  } finally {
    row._loadingClasses = false
  }
}

// ---- 新增 / 编辑 ----
const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({
  id: null, courseId: null, teacherId: null, semester: '2026秋',
  weeklySessions: 1, durationSlots: 1, classIds: []
})

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  teacherId: [{ required: true, message: '请选择教师', trigger: 'change' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'change' }],
  weeklySessions: [{ required: true, message: '请设置每周上课次数', trigger: 'change' }],
  durationSlots: [{ required: true, message: '请设置每次连续节数', trigger: 'change' }]
}

const courseOptions = ref([])
const teacherOptions = ref([])
const classOptions = ref([])

async function loadOptions() {
  const [courses, teachers, classes] = await Promise.all([
    listCourses(), listTeachers(), listClasses()
  ])
  courseOptions.value = courses.data || []
  teacherOptions.value = teachers.data || []
  classOptions.value = classes.data || []
}

async function openCreate() {
  editing.value = false
  Object.assign(form, {
    id: null, courseId: null, teacherId: null, semester: '2026秋',
    weeklySessions: 1, durationSlots: 1, classIds: []
  })
  dialogVisible.value = true
  await loadOptions()
}

async function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    courseId: row.courseId,
    teacherId: row.teacherId,
    semester: row.semester,
    weeklySessions: row.weeklySessions,
    durationSlots: row.durationSlots,
    classIds: []
  })
  dialogVisible.value = true
  await Promise.all([
    loadOptions(),
    listOfferingClasses(row.id).then((res) => {
      form.classIds = (res.data || []).map((c) => c.id)
    }).catch(() => {
      form.classIds = []
    })
  ])
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
    courseId: form.courseId,
    teacherId: form.teacherId,
    semester: (form.semester || '').trim(),
    weeklySessions: form.weeklySessions,
    durationSlots: form.durationSlots
  }
  saving.value = true
  try {
    let offeringId = form.id
    if (editing.value) {
      await updateCourseOffering(offeringId, payload)
    } else {
      const res = await createCourseOffering(payload)
      offeringId = res.data?.id
      if (!offeringId) throw new Error('创建未返回开课实例ID')
    }
    // 班级关联按编辑时选择覆盖保存(编辑清空选择=清空班级)
    if (editing.value || form.classIds.length > 0) {
      await replaceOfferingClasses(offeringId, form.classIds)
    }
    ElMessage.success(editing.value ? '开课实例修改成功' : '开课实例新增成功')
    dialogVisible.value = false
    await load()
  } catch (e) {
    // 校验/业务错误(如创建后被任务引用不能改名等)由 request 拦截器弹出后端 message
    if (e && !e.response) ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除开课实例「${row.courseName} - ${row.semester}」吗?\n将同时解除其班级关联; 若已被排课任务引用将被后端拒绝。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteCourseOffering(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    // 被排课任务引用等业务错误由 request 拦截器弹出后端 message
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
  line-height: 1.7;
  width: 100%;
}
.expand-panel {
  padding: 4px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.expand-label {
  font-size: 13px;
  color: #606266;
  margin-right: 4px;
}
.expand-empty {
  font-size: 13px;
  color: #c0c4cc;
}
.class-tag {
  margin-right: 4px;
}
</style>
