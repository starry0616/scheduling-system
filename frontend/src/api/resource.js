import request from './request'

// ============ 基础资源(课程/教师/班级/教室/开课/时间段) API ============
// 说明: 所有写接口均需 ADMIN; 读接口登录即可。统一使用 request.js 拦截器提示错误。

// ---- 课程 ----

/** 课程列表(keyword 可选模糊查询) */
export function listCourses(keyword) {
  return request.get('/courses', { params: { keyword } })
}

export function getCourse(id) {
  return request.get(`/courses/${id}`)
}

export function createCourse(data) {
  return request.post('/courses', data)
}

export function updateCourse(id, data) {
  return request.put(`/courses/${id}`, data)
}

export function deleteCourse(id) {
  return request.delete(`/courses/${id}`)
}

// ---- 教师 ----

/** 教师列表(keyword 可选模糊查询) */
export function listTeachers(keyword) {
  return request.get('/teachers', { params: { keyword } })
}

export function getTeacher(id) {
  return request.get(`/teachers/${id}`)
}

export function createTeacher(data) {
  return request.post('/teachers', data)
}

export function updateTeacher(id, data) {
  return request.put(`/teachers/${id}`, data)
}

export function deleteTeacher(id) {
  return request.delete(`/teachers/${id}`)
}

/** 可绑定教师的候选登录账号(ADMIN; role=TEACHER 且未绑定; currentTeacherId 可选=编辑时并入当前账号) */
export function listTeacherCandidates(currentTeacherId) {
  return request.get('/teachers/user-candidates', { params: { currentTeacherId } })
}

// ---- 班级 ----

/** 班级列表(keyword 可选模糊查询) */
export function listClasses(keyword) {
  return request.get('/classes', { params: { keyword } })
}

export function getClazz(id) {
  return request.get(`/classes/${id}`)
}

export function createClazz(data) {
  return request.post('/classes', data)
}

export function updateClazz(id, data) {
  return request.put(`/classes/${id}`, data)
}

export function deleteClazz(id) {
  return request.delete(`/classes/${id}`)
}

// ---- 教室 ----

/** 教室列表(keyword 可选模糊查询) */
export function listClassrooms(keyword) {
  return request.get('/classrooms', { params: { keyword } })
}

export function getClassroom(id) {
  return request.get(`/classrooms/${id}`)
}

export function createClassroom(data) {
  return request.post('/classrooms', data)
}

export function updateClassroom(id, data) {
  return request.put(`/classrooms/${id}`, data)
}

export function deleteClassroom(id) {
  return request.delete(`/classrooms/${id}`)
}

// ---- 开课实例 ----

/** 开课实例列表(semester 可选精确过滤) */
export function listCourseOfferings(semester) {
  return request.get('/course-offerings', { params: { semester } })
}

export function getCourseOffering(id) {
  return request.get(`/course-offerings/${id}`)
}

export function createCourseOffering(data) {
  return request.post('/course-offerings', data)
}

export function updateCourseOffering(id, data) {
  return request.put(`/course-offerings/${id}`, data)
}

export function deleteCourseOffering(id) {
  return request.delete(`/course-offerings/${id}`)
}

// ---- 开课实例-班级关联(覆盖式保存) ----

/** 查询某开课实例已关联班级 */
export function listOfferingClasses(offeringId) {
  return request.get(`/course-offerings/${offeringId}/classes`)
}

/** 覆盖式保存关联班级(classIds 空数组=清空) */
export function replaceOfferingClasses(offeringId, classIds) {
  return request.put(`/course-offerings/${offeringId}/classes`, { classIds })
}

// ---- 时间段 ----

/** 全量时间段(按 dayOfWeek, period 升序) */
export function listTimeSlots() {
  return request.get('/time-slots')
}
