<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="goHome">返回首页</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const goHome = () => {
  if (authStore.isLoggedIn) {
    router.push(`/${authStore.role.toLowerCase()}`)
  } else {
    router.push('/login')
  }
}
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  gap: 16px;
}

.not-found h1 {
  font-size: 80px;
  color: #409eff;
  margin: 0;
}

.not-found p {
  font-size: 18px;
  color: #909399;
}
</style>
