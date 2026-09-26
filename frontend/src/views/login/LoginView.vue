<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="login-title">基于模拟退火算法的自动排课系统</div>
      </template>
      <el-form ref="loginFormRef" :model="loginForm" :rules="rules" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" style="width: 100%" :loading="loading">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-hint">
        <span>默认管理员: admin / admin123</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)

const loginFormRef = ref()

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const data = await authStore.login(loginForm)
    ElMessage.success(`欢迎回来, ${data.user.realName}`)
    router.push(`/${data.user.role.toLowerCase()}`)
  } catch {
    // 错误已由 request 拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
}

.login-title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.login-hint {
  text-align: center;
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>
