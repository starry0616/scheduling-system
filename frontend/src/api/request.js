import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器: 添加JWT Token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器: 统一处理错误
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code && res.code !== 200) {
      if (!response.config.silentError) {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    if (error.response) {
      if (error.response.status === 401) {
        localStorage.removeItem('token')
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
      } else {
        if (!error.config?.silentError) {
          ElMessage.error(error.response.data?.message || '网络错误')
        }
      }
    } else {
      if (!error.config?.silentError) {
        ElMessage.error('网络连接失败')
      }
    }
    return Promise.reject(error)
  }
)

export default request
