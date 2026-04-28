import type { App, Directive } from 'vue'
import { useUserStore } from '@/store/modules/user'

/**
 * 权限指令
 * 用法：v-permission="['btn:add', 'btn:edit']"
 */
export const permission: Directive = {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const { permissions } = userStore.userInfo || {}

    if (Array.isArray(value) && value.length > 0) {
      const hasPermission = permissions?.some((permission: string) =>
        value.includes(permission)
      )
      if (!hasPermission) {
        el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('权限指令需要传入权限标识数组')
    }
  }
}

/**
 * 角色指令
 * 用法：v-role="['admin', 'editor']"
 */
export const role: Directive = {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const { roles } = userStore.userInfo || {}

    if (Array.isArray(value) && value.length > 0) {
      const hasRole = roles?.some((role: any) =>
        value.includes(role.roleKey)
      )
      if (!hasRole) {
        el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('角色指令需要传入角色标识数组')
    }
  }
}

/**
 * 权限按钮指令
 * 用法：v-auth="['system:user:add']"
 */
export const auth: Directive = {
  mounted(el, binding, vnode) {
    const { value } = binding
    const userStore = useUserStore()
    const { permissions } = userStore.userInfo || {}

    if (value && Array.isArray(value) && value.length > 0) {
      const hasAuth = permissions?.some((permission: string) =>
        value.includes(permission)
      )
      if (!hasAuth) {
        el.parentNode?.removeChild(el)
      }
    } else {
      el.parentNode?.removeChild(el)
    }
  }
}

/**
 * 指令别名
 */
export const vPermission = permission
export const vRole = role
export const vAuth = auth

/**
 * 设置权限指令
 */
export function setupPermissions(app: App) {
  app.directive('permission', permission)
  app.directive('role', role)
  app.directive('auth', auth)
}

export default {
  install(app: App) {
    setupPermissions(app)
  }
}
