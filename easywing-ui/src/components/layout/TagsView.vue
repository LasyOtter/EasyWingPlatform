<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePermissionStore } from '@/store/modules/permission'

const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()

const visitedViews = ref<any[]>([])
const affixTags = ref<any[]>([])

// 添加标签
const addView = (view: any) => {
  if (view.meta?.noTagsView) return
  if (visitedViews.value.some((v) => v.path === view.path)) return
  visitedViews.value.push({
    path: view.path,
    name: view.name,
    meta: { ...view.meta }
  })
}

// 关闭标签
const closeView = (view: any) => {
  const index = visitedViews.value.findIndex((v) => v.path === view.path)
  if (index > -1) {
    visitedViews.value.splice(index, 1)
    if (view.path === route.path) {
      const nextView = visitedViews.value[Math.max(0, index - 1)]
      router.push(nextView?.path || '/dashboard')
    }
  }
}

// 刷新标签
const refreshView = (view: any) => {
  router.replace({
    path: '/redirect' + view.path,
    query: { t: Date.now() }
  })
}

// 跳转到标签
const toView = (view: any) => {
  router.push(view.path)
}

// 是否是当前标签
const isActive = (view: any) => {
  return view.path === route.path
}

// 监听路由变化
watch(
  () => route.path,
  () => {
    addView(route)
  },
  { immediate: true }
)

// 初始化
const initTags = () => {
  const routes = permissionStore.routes
  affixTags.value = routes.filter((item) => item.meta?.isAffix)
  affixTags.value.forEach((tag) => {
    if (tag.name) {
      addView(tag)
    }
  })
}

initTags()
</script>

<template>
  <div class="tags-view-container">
    <scroll-pane class="tags-view-wrapper">
      <router-link
        v-for="tag in visitedViews"
        :key="tag.path"
        :to="{ path: tag.path }"
        tag="span"
        class="tags-view-item"
        :class="{ active: isActive(tag) }"
      >
        {{ tag.meta?.title }}
        <span
          v-if="!tag.meta?.noClose"
          class="el-icon-close"
          @click.prevent.stop="closeView(tag)"
        >
          <Close />
        </span>
      </router-link>
    </scroll-pane>
  </div>
</template>

<style lang="scss" scoped>
.tags-view-container {
  height: $tags-view-height;
  width: 100%;
  background: #fff;
  border-bottom: 1px solid #d8dce5;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.12), 0 0 3px 0 rgba(0, 0, 0, 0.04);

  .tags-view-wrapper {
    .tags-view-item {
      display: inline-block;
      position: relative;
      cursor: pointer;
      height: 26px;
      line-height: 26px;
      border: 1px solid #d8dce5;
      color: #495060;
      background: #fff;
      padding: 0 8px;
      font-size: 12px;
      margin-left: 5px;
      margin-top: 4px;
      border-radius: 4px;

      &:first-of-type {
        margin-left: 15px;
      }

      &:last-of-type {
        margin-right: 15px;
      }

      &.active {
        background-color: #409eff;
        color: #fff;
        border-color: #409eff;

        &::before {
          content: '';
          background: #fff;
          display: inline-block;
          width: 8px;
          height: 8px;
          border-radius: 50%;
          position: relative;
          margin-right: 2px;
        }
      }

      .el-icon-close {
        width: 16px;
        height: 16px;
        vertical-align: 2px;
        border-radius: 50%;
        text-align: center;
        transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
        transform-origin: 100% 50%;

        &:hover {
          background-color: #b4bccc;
          color: #fff;
        }
      }
    }
  }
}
</style>
