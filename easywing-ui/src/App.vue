<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'

const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

// 初始化
onMounted(async () => {
  // 检查 Token 是否存在
  const token = userStore.token
  if (token) {
    try {
      // 获取用户信息
      await userStore.getUserInfo()
      // 生成路由
      await permissionStore.generateRoutes()
    } catch (error) {
      console.error('Initialization failed:', error)
      ElMessage.error('获取用户信息失败，请重新登录')
      userStore.logout()
    }
  }
})
</script>

<template>
  <router-view />
</template>

<style>
#app {
  width: 100%;
  height: 100%;
}

html,
body {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

::-webkit-scrollbar-track {
  background: #f5f7fa;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

/* 隐藏滚动条但保持功能 */
.scrollbar-hidden {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.scrollbar-hidden::-webkit-scrollbar {
  display: none;
}
</style>
