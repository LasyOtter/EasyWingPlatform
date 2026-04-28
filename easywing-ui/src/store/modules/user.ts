import { defineStore } from 'pinia'
import { store } from '../index'
import type { UserInfo, LoginForm } from '@/types'
import { loginApi, getUserInfoApi, logoutApi } from '@/api/system/user'

interface UserState {
  token: string
  userInfo: UserInfo | null
  permissions: string[]
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: '',
    userInfo: null,
    permissions: []
  }),

  actions: {
    // 登录
    async login(loginForm: LoginForm) {
      try {
        const res: any = await loginApi(loginForm)
        this.token = res.token
        return res
      } catch (error) {
        throw error
      }
    },

    // 获取用户信息
    async getUserInfo() {
      try {
        const res: any = await getUserInfoApi()
        this.userInfo = res.user
        this.permissions = res.permissions || []
        return res
      } catch (error) {
        throw error
      }
    },

    // 登出
    async logout() {
      try {
        await logoutApi()
        this.token = ''
        this.userInfo = null
        this.permissions = []
      } catch (error) {
        // 即使失败也清理本地状态
        this.token = ''
        this.userInfo = null
        this.permissions = []
        throw error
      }
    },

    // 重置 Token
    resetToken() {
      this.token = ''
      this.userInfo = null
      this.permissions = []
    }
  },

  persist: {
    key: 'easywing-user',
    storage: localStorage,
    paths: ['token']
  }
})

// 在 setup 外使用
export function useUserStoreOutermost() {
  return useUserStore(store)
}
