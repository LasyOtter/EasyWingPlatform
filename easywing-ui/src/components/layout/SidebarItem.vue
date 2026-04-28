<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { isExternalLink } from '@/utils/route'

const props = defineProps<{
  item: any
  basePath: string
}>()

const router = useRouter()
const route = useRoute()

const hasOneShowingChild = computed(() => {
  const children = props.item.children || []
  const showingChildren = children.filter((item: any) => {
    return !item.meta?.isHide
  })

  return showingChildren.length === 1
})

const resolvePath = computed(() => {
  let path = props.item.path
  if (isExternalLink(props.item.path)) {
    return props.item.path
  }
  if (props.basePath && path) {
    return `${props.basePath}/${path}`.replace(/\/+/g, '/')
  }
  return path
})

const isActive = computed(() => {
  return route.path === resolvePath.value
})

const handleLink = () => {
  if (isExternalLink(props.item.path)) {
    window.open(props.item.path, '_blank')
  } else {
    router.push(resolvePath.value)
  }
}
</script>

<template>
  <template v-if="hasOneShowingChild && !item.alwaysShow">
    <el-menu-item
      v-if="item.meta"
      :index="resolvePath"
      :class="{ 'is-active': isActive }"
      @click="handleLink"
    >
      <el-icon v-if="item.meta?.icon">
        <component :is="item.meta.icon" />
      </el-icon>
      <template #title>
        <span>{{ item.meta?.title }}</span>
      </template>
    </el-menu-item>
  </template>

  <el-sub-menu v-else :index="resolvePath" :popper-append-to-body="true">
    <template #title>
      <el-icon v-if="item.meta?.icon">
        <component :is="item.meta.icon" />
      </el-icon>
      <span>{{ item.meta?.title }}</span>
    </template>
    <template v-for="child in item.children" :key="child.path">
      <template v-if="!child.meta?.isHide">
        <sidebar-item
          v-if="child.children && child.children.length > 0"
          :item="child"
          :base-path="resolvePath"
        />
        <el-menu-item v-else :index="child.path" @click="handleLink">
          <el-icon v-if="child.meta?.icon">
            <component :is="child.meta.icon" />
          </el-icon>
          <span>{{ child.meta?.title }}</span>
        </el-menu-item>
      </template>
    </template>
  </el-sub-menu>
</template>
