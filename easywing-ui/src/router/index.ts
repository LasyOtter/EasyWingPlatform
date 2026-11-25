import { createRouter, createWebHistory, type RouteRecordRaw, type Router } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import i18n from '@/locales'

// 静态路由
import Layout from '@/components/layout/index.vue'

// 免登录白名单
const whiteList = ['/login', '/404', '/500']

/** 应用标题，用于 document.title 前缀。 */
const APP_TITLE = import.meta.env.VITE_APP_TITLE || 'EasyWing Platform'

export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { titleKey: 'login.submit', isHide: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { titleKey: 'error.page404', isHide: true }
  },
  {
    path: '/500',
    name: 'InternalError',
    component: () => import('@/views/error/500.vue'),
    meta: { titleKey: 'error.page500', isHide: true }
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
    meta: { titleKey: 'route.dashboard', icon: 'home', isAffix: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { titleKey: 'route.dashboard', icon: 'home', isAffix: true, noTagsView: true }
      }
    ]
  },
  {
    path: '/system',
    component: Layout,
    redirect: '/system/user',
    meta: { titleKey: 'route.system', icon: 'setting' },
    children: [
      {
        path: 'user',
        name: 'UserManagement',
        component: () => import('@/views/system/user/index.vue'),
        meta: { titleKey: 'route.userManagement', icon: 'user' }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/system/role/index.vue'),
        meta: { titleKey: 'route.roleManagement', icon: 'role' }
      },
      {
        path: 'menu',
        name: 'MenuManagement',
        component: () => import('@/views/system/menu/index.vue'),
        meta: { titleKey: 'route.menuManagement', icon: 'menu' }
      },
      {
        path: 'dept',
        name: 'DeptManagement',
        component: () => import('@/views/system/dept/index.vue'),
        meta: { titleKey: 'route.deptManagement', icon: 'dept' }
      },
      {
        path: 'dict',
        name: 'DictManagement',
        component: () => import('@/views/system/dict/index.vue'),
        meta: { titleKey: 'route.dictManagement', icon: 'dict' }
      }
    ]
  },
  {
    path: '/profile',
    component: Layout,
    redirect: '/profile/index',
    meta: { titleKey: 'route.profile', icon: 'user' },
    children: [
      {
        path: 'index',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { titleKey: 'route.profile', noTagsView: true }
      }
    ]
  },
  {
    path: '/ai',
    component: Layout,
    redirect: '/ai/chat',
    meta: { titleKey: 'route.ai', icon: 'chat' },
    children: [
      {
        path: 'chat',
        name: 'AiChat',
        component: () => import('@/views/ai/chat/index.vue'),
        meta: { titleKey: 'route.aiChat', icon: 'chat' }
      },
      {
        path: 'agent',
        name: 'AiAgent',
        component: () => import('@/views/ai/agent/index.vue'),
        meta: { titleKey: 'route.aiAgent', icon: 'magic-stick' }
      },
      {
        path: 'model',
        name: 'AiModel',
        component: () => import('@/views/ai/model/index.vue'),
        meta: { titleKey: 'route.aiModel', icon: 'model' }
      }
    ]
  },
  {
    path: '/aid',
    component: Layout,
    redirect: '/aid/project',
    meta: { titleKey: 'route.aid', icon: 'magic-stick' },
    children: [
      {
        path: 'project',
        name: 'AidProject',
        component: () => import('@/views/aid/project/index.vue'),
        meta: { titleKey: 'route.aidProject', icon: 'folder' }
      },
      {
        path: 'asset',
        name: 'AidAsset',
        component: () => import('@/views/aid/asset/index.vue'),
        meta: { titleKey: 'route.aidAsset', icon: 'picture' }
      },
      {
        path: 'visual-setting',
        name: 'AidVisualSetting',
        component: () => import('@/views/aid/visual-setting/index.vue'),
        meta: { titleKey: 'route.aidVisualSetting', icon: 'brush' }
      },
      {
        path: 'episode',
        name: 'AidEpisode',
        component: () => import('@/views/aid/episode/index.vue'),
        meta: { titleKey: 'route.aidEpisode', icon: 'film' }
      },
      {
        path: 'shot',
        name: 'AidShot',
        component: () => import('@/views/aid/shot/index.vue'),
        meta: { titleKey: 'route.aidShot', icon: 'video-camera' }
      },
      {
        path: 'task',
        name: 'AidTask',
        component: () => import('@/views/aid/task/index.vue'),
        meta: { titleKey: 'route.aidTask', icon: 'list' }
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
  router.getRoutes().forEach((route) => {
    const { name } = route
    if (name && !whiteList.includes(String(name))) {
      router.hasRoute(name) && router.removeRoute(name)
    }
  })
}

/**
 * 把路由 meta.titleKey 解析为当前语言的标题。
 *
 * 未声明 titleKey 时回退到 name，保证后端下发的动态菜单（只有 title）仍可显示。
 */
export function resolveRouteTitle(route: { meta?: Record<string, any>; name?: string }): string {
  const key = route.meta?.titleKey
  if (key && i18n.global.te(key)) {
    return i18n.global.t(key)
  }
  if (route.meta?.title) {
    return String(route.meta.title)
  }
  return route.name ? String(route.name) : ''
}

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const hasToken = userStore.token

  const title = resolveRouteTitle(to as any)
  document.title = title ? `${title} - ${APP_TITLE}` : APP_TITLE

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
