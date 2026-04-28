<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { removeToken } from '@/utils/auth'

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void
}>()

const router = useRouter()
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo)
const nickName = computed(() => userInfo.value?.nickName || userInfo.value?.userName || 'Admin')

// 退出登录
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await userStore.logout()
    removeToken()
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch {
    // 取消操作
  }
}

// 个人中心
const goProfile = () => {
  router.push('/profile/index')
}
</script>

<template>
  <div class="header">
    <!-- 左侧菜单 -->
    <div class="header-left">
      <hamburger
        :is-active="true"
        class="hamburger"
        @toggle="emit('toggle-sidebar')"
      />
      <breadcrumb class="breadcrumb" />
    </div>

    <!-- 右侧操作 -->
    <div class="header-right">
      <!-- 全屏 -->
      <screenfull class="header-action" />

      <!-- 消息通知 -->
      <el-badge :value="3" class="header-action">
        <el-icon :size="20">
          <Bell />
        </el-icon>
      </el-badge>

      <!-- 用户信息 -->
      <el-dropdown class="user-dropdown" trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" src="" />
          <span class="username">{{ nickName }}</span>
          <el-icon class="el-icon--right">
            <arrow-down />
          </el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="setting">
              <el-icon><Setting /></el-icon>
              账号设置
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, h } from 'vue'

// 汉堡包图标组件
const Hamburger = defineComponent({
  name: 'Hamburger',
  props: {
    isActive: {
      type: Boolean,
      default: false
    }
  },
  setup(props) {
    return () =>
      h('div', { class: ['hamburger', props.isActive && 'is-active'] }, [
        h('svg', { viewBox: '0 0 1024 1024', class: 'hamburger-icon' }, [
          h('path', {
            d:
              'M408 442h480c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8H408c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8zm-8 204c0 4.4 3.6 8 8 8h480c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8H408c-4.4 0-8 3.6-8 8v56zm504-486H120c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8h784c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8zm0 632H120c-4.4 0-8 3.6-8 8v56c0 4.4 3.6 8 8 8h784c4.4 0 8-3.6 8-8v-56c0-4.4-3.6-8-8-8zM142.4 642.1L298.7 519a8.84 8.84 0 0 0 0-13.9L142.4 381.9c-5.8-4.6-14.4-.5-14.4 6.9v246.3a8.9 8.9 0 0 0 14.4 7z',
            fill: 'currentColor'
          })
        ])
      ])
  }
})

// 全屏组件
const Screenfull = defineComponent({
  name: 'Screenfull',
  setup() {
    const isFullscreen = ref(false)

    const toggle = () => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen()
        isFullscreen.value = true
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen()
          isFullscreen.value = false
        }
      }
    }

    return () =>
      h('div', { class: 'header-action', onClick: toggle }, [
        h(isFullscreen.value ? 'Close' : 'FullScreen')
      ])
  }
})

export default {
  components: {
    Hamburger,
    Screenfull
  }
}
</script>

<style lang="scss" scoped>
.header {
  height: $header-height;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);

  .header-left {
    display: flex;
    align-items: center;

    .hamburger {
      cursor: pointer;
    }

    .breadcrumb {
      margin-left: 16px;
    }
  }

  .header-right {
    display: flex;
    align-items: center;

    .header-action {
      padding: 0 12px;
      cursor: pointer;
      color: #606266;
      display: flex;
      align-items: center;

      &:hover {
        color: #409eff;
      }
    }

    .user-dropdown {
      margin-left: 16px;

      .user-info {
        display: flex;
        align-items: center;
        cursor: pointer;

        .username {
          margin-left: 8px;
          font-size: 14px;
        }
      }
    }
  }
}
</style>
