<template>
  <div class="schedule-page" v-loading="loading">
    <el-page-header :content="`标准周课表`" @back="router.back()" />

    <template v-if="state === 'ready'">
      <el-card shadow="never" class="toolbar" style="margin-top: 14px">
        <div class="toolbar-inner">
          <div class="tool-left">
            <span class="label">查询维度</span>
            <el-radio-group v-model="dimension" @change="onDimensionChange">
              <el-radio-button value="class">按班级</el-radio-button>
              <el-radio-button value="teacher">按教师</el-radio-button>
              <el-radio-button value="classroom">按教室</el-radio-button>
            </el-radio-group>
            <el-select v-model="filterId" placeholder="请选择" style="width: 230px; margin-left: 10px" clearable filterable>
              <el-option v-for="o in filterOptions" :key="o.id" :label="o.label" :value="o.id" />
            </el-select>
          </div>
          <div class="tool-right">
            <el-tag type="success" effect="plain">结果ID: {{ summary.resultId }}</el-tag>
            <el-tag :type="summary.feasible ? 'success' : 'warning'" effect="plain">
              {{ summary.outcome }} / 硬冲突 {{ summary.hardViolation }}
            </el-tag>
            <el-button :icon="Refresh" size="small" @click="loadAll">刷新</el-button>
          </div>
        </div>
        <div class="legend">
          <span class="legend-item"><i class="dot dot-theory"></i>理论课</span>
          <span class="legend-item"><i class="dot dot-lab"></i>实验课(按 durationSlots 连续跨大节)</span>
          <span class="legend-item"><i class="dot dot-block"></i>当前筛选 {{ filtered.length }} 条</span>
        </div>
      </el-card>

      <el-card shadow="never" class="grid-card" style="margin-top: 14px">
        <el-empty v-if="filtered.length === 0" description="该维度下暂无排课记录，请切换维度" style="padding: 40px" />
        <div v-else class="table-scroll">
          <table class="sched-table">
            <thead>
              <tr>
                <th class="time-col">节次 / 时间</th>
                <th v-for="d in 5" :key="'h' + d">{{ dayNames[d] }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in 5" :key="'r' + p">
                <td class="time-col">
                  <div class="period-no">第{{ p }}节</div>
                  <div class="period-time">{{ periodTime(p) }}</div>
                </td>
                <template v-for="d in 5" :key="'c' + p + '-' + d">
                  <!-- 已被同列上方 rowspan 覆盖的格: 不输出 -->
                  <td v-if="!isCovered(p, d)" class="cell-cell" :rowspan="rowSpan(p, d)">
                    <template v-if="cellEntries(p, d).length">
                      <div
                        v-for="e in cellEntries(p, d)"
                        :key="e.entryId"
                        class="lesson-block"
                        :class="e.isLabCourse ? 'is-lab' : 'is-theory'"
                        :title="lessonTitle(e)"
                      >
                        <div class="lb-course">
                          {{ e.courseName || e.courseCode }}
                          <el-tag v-if="e.isLabCourse" size="small" type="warning" effect="dark" class="lb-dur">连续{{ e.durationSlots }}节</el-tag>
                        </div>
                        <div class="lb-line">教师：{{ e.teacherName || '-' }}</div>
                        <div class="lb-line">教室：{{ e.building || '' }}{{ e.roomNo || '-' }}</div>
                        <div class="lb-line">班级：{{ (e.classes || []).map((c) => c.className).join('、') || '-' }}</div>
                      </div>
                    </template>
                    <div v-else class="cell-empty"></div>
                  </td>
                </template>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="grid-tip">
          说明：纵轴为排课大节(第1~5节)。跨两节课程依据实际 TimeSlot 的 dayOfWeek+period 与 offering.durationSlots 渲染为纵向跨两行的整块，
          不使用 slotId 连续推断。
        </div>
      </el-card>
    </template>

    <template v-else-if="state === 'empty'">
      <el-empty description="该任务暂无可查看的排课结果，请先执行排课">
        <el-button type="primary" @click="router.back()">返回上一页</el-button>
      </el-empty>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getResultByTask, getScheduleByResult } from '@/api/schedulingResult'
import { DAY_NAMES } from '@/utils/dict'

const route = useRoute()
const router = useRouter()
const taskId = route.params.taskId || route.params.id

const dayNames = DAY_NAMES
const loading = ref(false)
const state = ref('loading') // loading | ready | empty
const summary = ref(null)
const entries = ref([])

const dimension = ref('class')
const filterId = ref(null)

function lessonTitle(e) {
  const cls = (e.classes || []).map((c) => c.className).join('、')
  return `${e.courseName || ''}(${e.courseCode || ''}) | 教师:${e.teacherName || ''} | 教室:${e.roomNo || ''} | 班级:${cls || '-'}`
}

// ---------- 维度选项 ----------
const filterOptions = computed(() => {
  const map = new Map()
  for (const e of entries.value) {
    if (dimension.value === 'class') {
      for (const c of e.classes || []) {
        if (!map.has(c.id)) map.set(c.id, { id: c.id, label: c.grade ? `${c.className}(${c.grade})` : c.className })
      }
    } else if (dimension.value === 'teacher') {
      if (e.teacherId && !map.has(e.teacherId)) {
        map.set(e.teacherId, { id: e.teacherId, label: e.teacherNo ? `${e.teacherName}(${e.teacherNo})` : e.teacherName })
      }
    } else if (dimension.value === 'classroom') {
      if (e.classroomId && !map.has(e.classroomId)) {
        map.set(e.classroomId, { id: e.classroomId, label: `${e.building || ''} ${e.roomNo || ''}`.trim() })
      }
    }
  }
  return [...map.values()].sort((a, b) => String(a.label).localeCompare(String(b.label), 'zh'))
})

function onDimensionChange() {
  filterId.value = null
  const opts = filterOptions.value
  if (opts.length) filterId.value = opts[0].id
}

// ---------- 筛选 ----------
const filtered = computed(() => {
  if (!entries.value.length) return []
  if (filterId.value === null || filterId.value === undefined || filterId.value === '') return entries.value
  const id = Number(filterId.value)
  if (dimension.value === 'class') {
    return entries.value.filter((e) => (e.classes || []).some((c) => Number(c.id) === id))
  }
  if (dimension.value === 'teacher') {
    return entries.value.filter((e) => Number(e.teacherId) === id)
  }
  return entries.value.filter((e) => Number(e.classroomId) === id)
})

// ---------- 网格计算(全部依据 dayOfWeek + period, 禁止 slotId 推导) ----------
function cellEntries(p, d) {
  return filtered.value.filter((e) => Number(e.dayOfWeek) === d && Number(e.period) === p)
}
function durOf(e) {
  return Math.max(1, Number(e.durationSlots) || 1)
}
function rowSpan(p, d) {
  const list = cellEntries(p, d)
  if (!list.length) return 1
  return Math.max(...list.map(durOf))
}
function isCovered(p, d) {
  return filtered.value.some(
    (e) => Number(e.dayOfWeek) === d && Number(e.period) < p && Number(e.period) + durOf(e) > p
  )
}

const periodTimes = ref({})
function periodTime(p) {
  return periodTimes.value[p] || ''
}
function buildPeriodTimes() {
  const map = {}
  for (const e of entries.value) {
    const p = Number(e.period)
    if (!map[p] && e.startTime && e.endTime) map[p] = `${e.startTime} - ${e.endTime}`
  }
  periodTimes.value = map
}

// ---------- 数据加载 ----------
async function loadAll() {
  loading.value = true
  state.value = 'loading'
  try {
    const s = await getResultByTask(taskId, true)
    summary.value = s.data || null
    if (!summary.value) {
      state.value = 'empty'
      return
    }
    const res = await getScheduleByResult(summary.value.resultId, true)
    entries.value = res.data || []
    buildPeriodTimes()
    state.value = 'ready'
    if (filterOptions.value.length && !filterId.value) {
      filterId.value = filterOptions.value[0].id
    }
  } catch {
    summary.value = null
    state.value = 'empty'
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.schedule-page {
  min-width: 900px;
}
.toolbar-inner {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.label {
  color: #606266;
  margin-right: 4px;
}
.legend {
  margin-top: 12px;
  display: flex;
  gap: 16px;
  align-items: center;
}
.legend-item {
  font-size: 12px;
  color: #909399;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}
.dot-theory {
  background: #ecf5ff;
  border: 1px solid #a0cfff;
}
.dot-lab {
  background: #fdf6ec;
  border: 1px solid #eebe77;
}
.dot-block {
  background: #67c23a;
}
.table-scroll {
  overflow-x: auto;
}
.sched-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.sched-table th,
.sched-table td {
  border: 1px solid #ebeef5;
  text-align: center;
  vertical-align: top;
}
.sched-table th {
  background: #f5f7fa;
  font-weight: 500;
  padding: 8px;
  color: #303133;
}
.time-col {
  width: 120px;
  min-width: 120px;
  background: #fafafa;
}
.period-no {
  font-weight: 600;
  color: #303133;
  padding-top: 6px;
}
.period-time {
  font-size: 12px;
  color: #909399;
  padding-bottom: 6px;
}
.cell-cell {
  height: 92px;
  min-width: 150px;
  padding: 3px;
  background: #fff;
}
.lesson-block {
  height: 100%;
  border-radius: 4px;
  padding: 6px 8px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  text-align: left;
  cursor: default;
  overflow: hidden;
}
.lesson-block + .lesson-block {
  margin-top: 4px;
}
.is-theory {
  background: #ecf5ff;
  border: 1px solid #a0cfff;
  color: #1f4e79;
}
.is-lab {
  background: #fdf6ec;
  border: 1px solid #eebe77;
  color: #7a5410;
}
.lb-course {
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.lb-line {
  font-size: 12px;
  opacity: 0.92;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.grid-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
