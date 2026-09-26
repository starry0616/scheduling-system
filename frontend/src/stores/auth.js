import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/api/request'

/**
 * 认证 Store - 管理用户登录状态
 */
export const useAuthStore = defineStore('auth', () => {
  // 从 localStorage 恢复初始状态
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') || '')
  const username = ref(localStorage.getItem('username') || '')
  const realName = ref(localStorage.getItem('realName') || '')
  const role = ref(localStorage.getItem('role') || '')

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 用户登录
   */
  async function login(loginForm) {
    const res = await request.post('/auth/login', loginForm)

    // 后端返回嵌套结构: data = { token, user: { id, username, realName, role, ... } }
    const user = res.data.user

    token.value = res.data.token
    userId.value = user.id
    username.value = user.username
    realName.value = user.realName
    role.value = user.role

    // 持久化到 localStorage
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('userId', user.id)
    localStorage.setItem('username', user.username)
    localStorage.setItem('realName', user.realName)
    localStorage.setItem('role', user.role)

    return res.data
  }

  /**
   * 获取当前用户信息 (刷新页面后恢复状态)
   */
  async function fetchUserInfo() {
    if (!token.value) return null

    try {
      const res = await request.get('/auth/me')
      // /auth/me 返回 data = user 对象 { id, username, realName, role, ... }
      const user = res.data
      userId.value = user.id
      username.value = user.username
      realName.value = user.realName
      role.value = user.role
      localStorage.setItem('userId', user.id)
      localStorage.setItem('username', user.username)
      localStorage.setItem('realName', user.realName)
      localStorage.setItem('role', user.role)
      return user
    } catch {
      // Token 失效，清除
      logout()
      return null
    }
  }

  /**
   * 登出
   */
  async function logout() {
    try {
      if (token.value) {
        await request.post('/auth/logout')
      }
    } catch {
      // 忽略登出请求错误
    }

    // 清除状态
    token.value = ''
    userId.value = ''
    username.value = ''
    realName.value = ''
    role.value = ''

    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('role')
  }

  return {
    token,
    userId,
    username,
    realName,
    role,
    isLoggedIn,
    login,
    fetchUserInfo,
    logout
  }
})
