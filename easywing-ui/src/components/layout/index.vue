<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'
import TagsView from './TagsView.vue'
import { usePermissionStore } from '@/store/modules/permission'

const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()

const isCollapse = ref(false)

// 切换侧边栏折叠
const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value
}

// 获取缓存的路由名称
const cachedViews = computed(() => {
  return permissionStore.routes
    .filter((item) => item.meta?.isKeepAlive)
    .map((item) => String(item.name))
})

// 关闭当前标签
const closeCurrentTag = (view: any) => {
  router.push('/dashboard')
}

onMounted(() => {
  // 初始化
})
</script>

<template>
  <div class="app-wrapper">
    <!-- 侧边栏 -->
    <Sidebar :collapse="isCollapse" class="sidebar-container" />

    <!-- 主内容区 -->
    <div class="main-container" :class="{ 'sidebar-collapse': isCollapse }">
      <!-- 顶部导航 -->
      <Header @toggle-sidebar="toggleSidebar" />

      <!-- 标签导航 -->
      <TagsView class="tags-view-container" />

      <!-- 内容区 -->
      <main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.app-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
}

.sidebar-container {
  width: $sidebar-width;
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  background: $menu-background;
  transition: width 0.28s;
  z-index: 1001;
  overflow: hidden;

  &.sidebar-collapse {
    width: $sidebar-collapse-width;
  }
}

.main-container {
  flex: 1;
  margin-left: $sidebar-width;
  min-height: 100vh;
  transition: margin-left 0.28s;
  display: flex;
  flex-direction: column;

  &.sidebar-collapse {
    margin-left: $sidebar-collapse-width;
  }
}

.tags-view-container {
  height: $tags-view-height;
  background: $tags-view-background;
  border-bottom: 1px solid $tags-view-border;
}

.app-main {
  flex: 1;
  padding: 16px;
  background: $main-background;
  overflow-y: auto;
  min-height: calc(100vh - #{$header-height + $tags-view-height});
}
</style>

<style lang="scss">
// 过渡动画
.fade-transform-leave-active,
.fade-transform-enter-active {
  transition: all 0.5s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>
