import { createRouter, createWebHistory, type RouteRecordRaw, type Router } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

// 静态路由
import Layout from '@/components/layout/index.vue'

export const constantRoutes: RouteRecordRaw[] = [
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

// 需要权限的路由
export const asyncRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    meta: { title: '首页', icon: 'home', isAffix: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'home', isAffix: true, noTagsView: true }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/user',
    meta: { title: '系统管理', icon: 'setting' },
    children: [
      {
        path: 'user',
        name: 'UserManagement',
        component: () => import('@/views/system/user/index.vue'),
        meta: { title: '用户管理', icon: 'user' }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/system/role/index.vue'),
        meta: { title: '角色管理', icon: 'role' }
      },
      {
        path: 'menu',
        name: 'MenuManagement',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { title: '菜单管理', icon: 'menu' }
      },
      {
        path: 'dept',
        name: 'DeptManagement',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { title: '部门管理', icon: 'dept' }
      },
      {
        path: 'dict',
        name: 'DictManagement',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { title: '字典管理', icon: 'dict' }
      }
    ]
  },
  {
    path: '/profile',
    component: Layout,
    redirect: '/profile/index',
    meta: { title: '个人中心', icon: 'user' },
    children: [
      {
        path: 'index',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', noTagsView: true }
      }
    ]
  }
]

// 创建路由实例
const router: Router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

// 重置路由
export function resetRouter() {
  const whiteList = ['/login', '/404', '/500']
  router.getRoutes().forEach((route) => {
    const { name } = route
    if (name && !whiteList.includes(String(name))) {
      router.hasRoute(name) && router.removeRoute(name)
    }
  })
}

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const hasToken = userStore.token

  if (hasToken) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      const hasUserInfo = userStore.userInfo?.userId
      if (hasUserInfo) {
        next()
      } else {
        try {
          await userStore.getUserInfo()
          next({ ...to, replace: true })
        } catch {
          await userStore.logout()
          next(`/login?redirect=${to.path}`)
        }
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
