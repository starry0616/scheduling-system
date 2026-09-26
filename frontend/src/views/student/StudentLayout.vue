<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">自动排课系统</div>
      <el-menu router default-active="/student" class="sidebar-menu" background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff">
        <el-menu-item index="/student">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="header-role">学生端</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              {{ authStore.realName || '学生' }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const handleCommand = async (command) => {
  if (command === 'logout') {
    await authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container { height: 100vh; }
.sidebar { background-color: #304156; }
.logo { height: 60px; line-height: 60px; text-align: center; color: #fff; font-size: 16px; font-weight: 500; }
.sidebar-menu { border: none; }
.header { display: flex; justify-content: space-between; align-items: center; background-color: #fff; border-bottom: 1px solid #e6e6e6; }
.header-left { display: flex; align-items: center; }
.header-role { font-size: 14px; color: #606266; }
.header-right { display: flex; align-items: center; }
.user-info { cursor: pointer; display: flex; align-items: center; font-size: 14px; color: #606266; }
</style>
