import { defineStore } from 'pinia'
import { authApi } from '@/api'

function restoreUser(): any {
  try {
    return JSON.parse(localStorage.getItem('bn_user') || 'null')
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('bn_token') || '',
    user: restoreUser()
  }),
  getters: {
    displayName: (state) => state.user?.realName || state.user?.username || '未登录'
  },
  actions: {
    async login(username: string, password: string) {
      const data: any = await authApi.login(username, password)
      this.token = data.accessToken
      this.user = data.user
      localStorage.setItem('bn_token', data.accessToken)
      localStorage.setItem('bn_user', JSON.stringify(data.user))
      return data
    },
    async loadMe() {
      if (!this.token) {
        return
      }
      const data: any = await authApi.me()
      this.user = data
      localStorage.setItem('bn_user', JSON.stringify(data))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('bn_token')
      localStorage.removeItem('bn_user')
    }
  }
})
