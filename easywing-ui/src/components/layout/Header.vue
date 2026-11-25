<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { removeToken } from '@/utils/auth'
import { setLocale, SUPPORT_LOCALES, getCurrentLocale, type AppLocale } from '@/locales'

const emit = defineEmits<{
  (e: 'toggle-sidebar'): void
}>()

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo)
const nickName = computed(() => userInfo.value?.nickName || userInfo.value?.userName || 'Admin')

const currentLocale = ref<AppLocale>(getCurrentLocale())
const supportLocales = [...SUPPORT_LOCALES]

/**
 * 切换语言。Element Plus 的内置文案包在 main.ts 中静态注册，
 * 需要重新加载页面才能整体生效。
 */
const handleLocaleChange = (locale: AppLocale) => {
  setLocale(locale)
  currentLocale.value = locale
  window.location.reload()
}

// 退出登录
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm(t('header.logoutConfirm'), t('common.tip'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })

    await userStore.logout()
    removeToken()
    router.push('/login')
    ElMessage.success(t('header.loggedOut'))
  } catch {
    // 取消操作
  }
}

// 个人中心
const goProfile = () => {
  router.push('/profile/index')
}

// 下拉菜单命令
const handleCommand = (command: string) => {
  if (command === 'profile') {
    goProfile()
  } else if (command === 'logout') {
    handleLogout()
  }
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

      <!-- 语言切换 -->
      <el-dropdown
        class="header-action"
        trigger="click"
        @command="handleLocaleChange"
      >
        <el-icon :size="20">
          <LocaleIcon />
        </el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="locale in supportLocales"
              :key="locale"
              :command="locale"
              :disabled="locale === currentLocale"
            >
              {{ t(`lang.${locale}`) }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

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
              {{ t('header.profile') }}
            </el-dropdown-item>
            <el-dropdown-item command="setting">
              <el-icon><Setting /></el-icon>
              {{ t('header.settings') }}
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              {{ t('header.logout') }}
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

// 语言切换图标（Element Plus 未内置 globe 类图标）
const LocaleIcon = defineComponent({
  name: 'LocaleIcon',
  setup() {
    return () =>
      h('svg', { viewBox: '0 0 1024 1024', width: '1em', height: '1em' }, [
        h('path', {
          d:
            'M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm273.4 720H238.6c12-66.6 44.2-126.4 92.6-172.4 16.2 13.2 34.2 24.4 53.6 33.2-6.6 26.4-13.4 56.6-19 88.4h64v-96h56v96h64c-5.6-31.8-12.4-62-19-88.4 19.4-8.8 37.4-20 53.6-33.2 48.4 46 80.6 105.8 92.6 172.4zM311.6 351.2c-6.6-12-12.6-24.6-18-37.8-10.8 8.8-21 18.4-30.6 28.6 5.4 13.2 11.4 25.8 18 37.8zm385.6 37.8c-9.6-10.2-19.8-19.8-30.6-28.6-5.4 13.2-11.4 25.8-18 37.8l18 37.8c6.6-12 12.6-24.6 18-37.8zM512 288c-40.8 0-79.2 12.6-111 34.4 8.4 19.8 17.8 38.8 28 56.8 24-11.4 50.6-18 78.4-19.2V288zm0 56v72c27.8 1.2 54.4 7.8 78.4 19.2 10.2-18 19.6-37 28-56.8-31.8-21.8-70.2-34.4-111-34.4zM268.4 512c0-19.2 2.4-37.8 6.8-55.8-14.4-6.2-28.2-13.4-41.4-21.4C219.4 456 210 483 204.8 512h63.6zm423.6 0c-5.2-29-14.6-56-29-77.2-13.2 8-27 15.2-41.4 21.4 4.4 18 6.8 36.6 6.8 55.8h63.6z',
          fill: 'currentColor'
        })
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
    Screenfull,
    LocaleIcon
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
