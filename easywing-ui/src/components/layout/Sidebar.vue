<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePermissionStore } from '@/store/modules/permission'
import SidebarItem from './SidebarItem.vue'
import variables from '@/assets/styles/variables.module.scss'

defineProps<{
  collapse: boolean
}>()

const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()

// 获取菜单列表
const menuList = computed(() => permissionStore.routes)

// 是否只有一个菜单
const onlyOneChild = ref<any>(null)

// 是否有子菜单
const hasOneShowingChild = (children: any[], parent: any) => {
  const showingChildren = children.filter((item: any) => {
    if (item.meta?.isHide) {
      return false
    }
    return true
  })

  if (showingChildren.length === 1) {
    onlyOneChild.value = showingChildren[0]
    return true
  }

  if (showingChildren.length === 0) {
    onlyOneChild.value = { ...parent, path: '', noShowingChildren: true }
    return true
  }

  return false
}

// 解决菜单点击空白问题
const isActive = (path: string) => {
  return route.path === path
}
</script>

<template>
  <div class="sidebar">
    <!-- Logo 区域 -->
    <div class="sidebar-logo">
      <img v-if="collapse" src="@/assets/images/logo-small.png" alt="logo" class="logo-small" />
      <img v-else src="@/assets/images/logo.png" alt="logo" class="logo" />
      <transition name="fade">
        <span v-if="!collapse" class="logo-title">EasyWing</span>
      </transition>
    </div>

    <!-- 菜单 -->
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="route.path"
        :collapse="collapse"
        :collapse-transition="false"
        :unique-opened="true"
        background-color="transparent"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        mode="vertical"
      >
        <template v-for="item in menuList" :key="item.path">
          <template v-if="!item.meta?.isHide">
            <SidebarItem
              v-if="hasOneShowingChild(item.children, item)"
              :item="onlyOneChild"
              :base-path="item.path"
            />
            <el-sub-menu v-else :index="item.path" :popper-append-to-body="true">
              <template #title>
                <el-icon v-if="item.meta?.icon">
                  <component :is="item.meta.icon" />
                </el-icon>
                <span>{{ item.meta?.title }}</span>
              </template>
              <SidebarItem
                v-for="child in item.children"
                :key="child.path"
                :item="child"
                :base-path="child.path"
              />
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<style lang="scss" scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;

  .sidebar-logo {
    height: $header-height;
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    .logo {
      height: 32px;
      margin-right: 8px;
    }

    .logo-small {
      height: 28px;
    }

    .logo-title {
      font-size: 20px;
      font-weight: 600;
      color: #fff;
      letter-spacing: 2px;
    }
  }

  .scrollbar-wrapper {
    flex: 1;
    overflow-x: hidden;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
