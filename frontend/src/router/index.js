import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, role: 'ADMIN' },
    children: [
      {
        path: '',
        name: 'AdminHome',
        component: () => import('@/views/admin/AdminHome.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'tasks',
        name: 'AdminTaskList',
        component: () => import('@/views/admin/SchedulingTaskList.vue'),
        meta: { title: '排课任务' }
      },
      {
        path: 'tasks/:id',
        name: 'AdminTaskDetail',
        component: () => import('@/views/admin/SchedulingTaskDetail.vue'),
        meta: { title: '任务详情' }
      },
      {
        path: 'tasks/:id/result',
        name: 'AdminTaskResult',
        component: () => import('@/views/admin/SchedulingResult.vue'),
        meta: { title: '排课结果' }
      },
      {
        path: 'tasks/:taskId/schedule',
        name: 'AdminTaskSchedule',
        component: () => import('@/views/schedule/ScheduleView.vue'),
        meta: { title: '标准周课表' }
      },
      {
        path: 'basic-data',
        redirect: '/admin/basic-data/courses'
      },
      {
        path: 'basic-data/courses',
        name: 'BasicDataCourses',
        component: () => import('@/views/admin/basicData/CourseManage.vue'),
        meta: { title: '课程管理' }
      },
      {
        path: 'basic-data/teachers',
        name: 'BasicDataTeachers',
        component: () => import('@/views/admin/basicData/TeacherManage.vue'),
        meta: { title: '教师管理' }
      },
      {
        path: 'basic-data/classes',
        name: 'BasicDataClasses',
        component: () => import('@/views/admin/basicData/ClazzManage.vue'),
        meta: { title: '班级管理' }
      },
      {
        path: 'basic-data/classrooms',
        name: 'BasicDataClassrooms',
        component: () => import('@/views/admin/basicData/ClassroomManage.vue'),
        meta: { title: '教室管理' }
      },
      {
        path: 'basic-data/offerings',
        name: 'BasicDataOfferings',
        component: () => import('@/views/admin/basicData/CourseOfferingManage.vue'),
        meta: { title: '开课实例' }
      }
    ]
  },
  {
    path: '/teacher',
    name: 'TeacherLayout',
    component: () => import('@/views/teacher/TeacherLayout.vue'),
    meta: { requiresAuth: true, role: 'TEACHER' },
    children: [
      {
        path: '',
        name: 'TeacherHome',
        component: () => import('@/views/teacher/TeacherHome.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'tasks',
        name: 'TeacherTaskList',
        component: () => import('@/views/teacher/TeacherScheduleList.vue'),
        meta: { title: '课表查看' }
      },
      {
        path: 'tasks/:taskId/schedule',
        name: 'TeacherTaskSchedule',
        component: () => import('@/views/schedule/ScheduleView.vue'),
        meta: { title: '标准周课表' }
      }
    ]
  },
  {
    path: '/student',
    name: 'StudentLayout',
    component: () => import('@/views/student/StudentLayout.vue'),
    meta: { requiresAuth: true, role: 'STUDENT' },
    children: [
      {
        path: '',
        name: 'StudentHome',
        component: () => import('@/views/student/StudentHome.vue'),
        meta: { title: '首页' }
      }
    ]
  },
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '404' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 自动排课系统` : '自动排课系统'

  const authStore = useAuthStore()

  // 已登录但 store 中没有 userId (页面刷新后 localStorage 有 token 但 store 被重置)
  // 重新从 localStorage 恢复
  if (authStore.token && !authStore.userId) {
    authStore.userId = localStorage.getItem('userId') || ''
    authStore.username = localStorage.getItem('username') || ''
    authStore.realName = localStorage.getItem('realName') || ''
    authStore.role = localStorage.getItem('role') || ''
  }

  if (to.meta.requiresAuth) {
    if (!authStore.isLoggedIn) {
      next('/login')
    } else if (to.meta.role && to.meta.role !== authStore.role) {
      // 已登录但角色不匹配，跳转到对应角色首页
      next(`/${authStore.role.toLowerCase()}`)
    } else {
      next()
    }
  } else {
    // 已登录用户访问登录页，跳转到对应首页
    if (authStore.isLoggedIn && to.path === '/login') {
      next(`/${authStore.role.toLowerCase()}`)
    } else {
      next()
    }
  }
})

export default router
