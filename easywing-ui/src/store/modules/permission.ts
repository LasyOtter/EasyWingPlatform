import { defineStore } from 'pinia'
import { store } from '../index'
import { resetRouter } from '@/router'
import type { RouteRecordRaw } from 'vue-router'
import { asyncRoutes } from '@/router'
import { getRouters } from '@/api/system/menu'

function filterAsyncRoutes(routes: any[], permissions: string[]) {
  const result: RouteRecordRaw[] = []
  routes.forEach((route) => {
    const item = { ...route }
    if (hasPermission(permissions, item)) {
      if (item.children) {
        item.children = filterAsyncRoutes(item.children, permissions)
      }
      result.push(item)
    }
  })
  return result
}

function hasPermission(permissions: string[], route: any) {
  if (route.meta && route.meta.permissions) {
    return permissions.some((permission: string) =>
      route.meta.permissions.includes(permission)
    )
  }
  return true
}

interface PermissionState {
  routes: RouteRecordRaw[]
  addRoutes: RouteRecordRaw[]
  isGenerate: boolean
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routes: [],
    addRoutes: [],
    isGenerate: false
  }),

  actions: {
    // 生成路由
    async generateRoutes(permissions?: string[]) {
      try {
        // 从后端获取路由
        const res: any = await getRouters()
        const accessedRoutes = res.menus || []

        // 过滤并转换路由
        this.addRoutes = filterAsyncRoutes(accessedRoutes, permissions || [])

        // 合并静态路由和动态路由
        this.routes = [...constantRoutes, ...this.addRoutes]

        this.isGenerate = true
        return this.addRoutes
      } catch (error) {
        console.error('Generate routes error:', error)
        throw error
      }
    },

    // 重置权限
    resetPermission() {
      this.routes = []
      this.addRoutes = []
      this.isGenerate = false
      resetRouter()
    }
  },

  persist: false
})

// 导入静态路由
import Layout from '@/components/layout/index.vue'
import dashboard from '@/router/modules/dashboard'
import system from '@/router/modules/system'
import profile from '@/router/modules/profile'

export const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', isHide: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', isHide: true }
  },
  {
    path: '/500',
    name: 'InternalError',
    component: () => import('@/views/error/500.vue'),
    meta: { title: '500', isHide: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    name: 'Any',
    meta: { title: '任意路由', isHide: true }
  }
]

export const keepAliveRoutes = ['Dashboard', 'Profile']

export function usePermissionStoreOutermost() {
  return usePermissionStore(store)
}
